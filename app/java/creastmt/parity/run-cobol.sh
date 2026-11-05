#!/usr/bin/env bash
# Runs the original CREASTMT job (CBSTM03A + CBSTM03B) under GnuCOBOL against
# the repo sample data and writes newline-delimited copies of the two
# fixed-length statement datasets to <out-dir>/STATEMNT.PS and STATEMNT.HTML.
#
# Usage: run-cobol.sh <out-dir>
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
OUT="${1:?usage: run-cobol.sh <out-dir>}"
DATA="$ROOT/app/data/ASCII"

command -v cobc >/dev/null || { echo "cobc (GnuCOBOL 3.x) not found on PATH" >&2; exit 2; }

rm -rf "$OUT"
mkdir -p "$OUT/build" "$OUT/work"
OUT="$(cd "$OUT" && pwd)"
BUILD="$OUT/build"
WORK="$OUT/work"

# Workaround 1: CBSTM03A starts by walking z/OS control blocks
# (PSA -> TCB -> TIOT) to DISPLAY the job/step name and DD list. Those
# addresses do not exist off-mainframe (PSAPTR is NULL -> SIGSEGV), so the
# compiled copy drops that diagnostic-only block. Nothing else is changed.
sed -e '/SET ADDRESS OF PSA-BLOCK   TO PSAPTR/,/OPEN OUTPUT STMT-FILE HTML-FILE/{
          /OPEN OUTPUT STMT-FILE HTML-FILE/!d
        }' "$ROOT/app/cbl/CBSTM03A.CBL" > "$BUILD/CBSTM03A.CBL"
cp "$ROOT/app/cbl/CBSTM03B.CBL" "$BUILD/CBSTM03B.CBL"
if diff -q "$ROOT/app/cbl/CBSTM03A.CBL" "$BUILD/CBSTM03A.CBL" >/dev/null; then
  echo "control-block patch did not apply" >&2; exit 3
fi

# Workaround 2: -fsign=EBCDIC so trailing overpunch signs in the sample data
# ('{', 'A'-'I' positive; '}', 'J'-'R' negative) are read as on z/OS.
# Workaround 3: CUSTREC.cpy indents 21 of its lines with two TAB characters
# (no tabs exist in z/OS source). -ftab-width=3 lands those lines at column
# 12, matching the space-indented CUST-ID line; the default width of 8 pushes
# PIC clauses past column 72 and the copybook fails to compile.
COBFLAGS=(-std=ibm -fsign=EBCDIC -ftab-width=3 -I "$ROOT/app/cpy" -Wno-others)
(
  cd "$BUILD"
  cobc -m "${COBFLAGS[@]}" CBSTM03B.CBL
  # Workaround 4: stub for the LE abend service CEE3ABD (error path only).
  cobc -m "${COBFLAGS[@]}" "$HERE/cobol/CEE3ABD.cbl"
  cobc -x "${COBFLAGS[@]}" -o CBSTM03A CBSTM03A.CBL
  cobc -x "${COBFLAGS[@]}" -o CBSTMLDR "$HERE/cobol/CBSTMLDR.cbl"
)

# STEP010 (SORT) equivalent:
#   SORT FIELDS=(263,16,CH,A,1,16,CH,A)
#   OUTREC FIELDS=(1:263,16,17:1,262,279:279,50)
# Input is the TRANSACT-layout sample (CVTRA05Y/CVTRA06Y, 350 bytes).
LC_ALL=C awk '{ r = sprintf("%-350s", $0);
                o = substr(r,263,16) substr(r,1,262) substr(r,279,50);
                printf "%-350s\n", o }' "$DATA/dailytran.txt" \
  | LC_ALL=C sort -s -t "$(printf '\t')" -k1.1,1.32 > "$WORK/TRXFL.SEQ"

# STEP020 (REPRO) + VSAM loads equivalent.
export COB_LIBRARY_PATH="$BUILD"
export XREFIN="$DATA/cardxref.txt"   XREFFILE="$WORK/CARDXREF.KSDS"
export CUSTIN="$DATA/custdata.txt"   CUSTFILE="$WORK/CUSTDATA.KSDS"
export ACCTIN="$DATA/acctdata.txt"   ACCTFILE="$WORK/ACCTDATA.KSDS"
export TRNXIN="$WORK/TRXFL.SEQ"      TRNXFILE="$WORK/TRXFL.KSDS"
"$BUILD/CBSTMLDR"

# STEP040: the program under test.
export STMTFILE="$WORK/STATEMNT.PS" HTMLFILE="$WORK/STATEMNT.HTML"
"$BUILD/CBSTM03A" > "$OUT/cbstm03a.sysout" 2>&1 || {
  rc=$?; cat "$OUT/cbstm03a.sysout" >&2; echo "CBSTM03A failed rc=$rc" >&2; exit $rc; }

# STMTFILE (LRECL 80) and HTMLFILE (LRECL 100) are fixed-length record
# datasets with no line terminators; split them one record per line.
to_lines() { # <file> <lrecl>
  local size; size=$(stat -c %s "$1")
  (( size % $2 == 0 )) || { echo "$1 is not a multiple of $2 bytes" >&2; exit 4; }
  LC_ALL=C fold -b -w "$2" "$1"; (( size > 0 )) && echo
}
to_lines "$WORK/STATEMNT.PS"   80  > "$OUT/STATEMNT.PS"
to_lines "$WORK/STATEMNT.HTML" 100 > "$OUT/STATEMNT.HTML"
echo "COBOL output: $OUT/STATEMNT.PS ($(wc -l < "$OUT/STATEMNT.PS") records)," \
     "$OUT/STATEMNT.HTML ($(wc -l < "$OUT/STATEMNT.HTML") records)"
