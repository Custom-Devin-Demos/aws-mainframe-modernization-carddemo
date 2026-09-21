#!/usr/bin/env bash
# Regenerate the CBACT04C golden fixtures under
# java/interest-service/src/test/resources/fixtures/<scenario>/expected/
# by actually running the COBOL program with GnuCOBOL.
#
#   tools/generate.sh                # all scenarios
#   tools/generate.sh single-account # one scenario
#
# Requirements: cobc (GnuCOBOL 3.x/4.x, indexed handler NOT needed), python3.
# See tools/cobol/README.md for what the harness changes relative to app/cbl/CBACT04C.cbl.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE="$(cd "$HERE/.." && pwd)"
REPO="$(cd "$MODULE/../.." && pwd)"
FIXTURES="$MODULE/src/test/resources/fixtures"
COBOL="$HERE/cobol"
PARM_DATE="${PARM_DATE:-2022071800}"     # INTCALC.jcl STEP15 PARM='2022071800'
BUILD="$MODULE/target/cobol"

command -v cobc >/dev/null || { echo "cobc (GnuCOBOL) not found" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 not found" >&2; exit 1; }

mkdir -p "$BUILD"

# 1. compile: patched CBACT04C (IBM dialect, EBCDIC-style zoned sign), CEE3ABD stub,
#    and the CBACT04D driver (needs ACCEPT ... FROM COMMAND-LINE, so default dialect).
cobc -c -std=ibm-strict -fsign=EBCDIC -I "$REPO/app/cpy" -o "$BUILD/CBACT04C.o" "$COBOL/CBACT04C-LS.cbl"
cobc -c -std=ibm-strict -fsign=EBCDIC -o "$BUILD/CEE3ABD.o" "$COBOL/CEE3ABD.cbl"
cobc -x -std=default -o "$BUILD/cbact04" "$COBOL/CBACT04D.cbl" "$BUILD/CBACT04C.o" "$BUILD/CEE3ABD.o"

# 2. (re)build the normalised input directories of every scenario.
python3 "$HERE/build_fixture_inputs.py" "$@"

# 3. run the program once per scenario.
scenarios=("$@")
if [ ${#scenarios[@]} -eq 0 ]; then
  scenarios=(sample-data nonzero-balances single-account)
fi

check_len() { # file expected-length
  local bad
  bad=$(awk -v n="$2" 'length($0) != n { print FILENAME ":" NR ": length " length($0) }' "$1")
  if [ -n "$bad" ]; then echo "$bad" >&2; exit 1; fi
}

for s in "${scenarios[@]}"; do
  in="$FIXTURES/$s/input"
  out="$FIXTURES/$s/expected"
  mkdir -p "$out"
  rm -f "$out/systran.txt" "$out/acctdata.txt"
  echo "running CBACT04C for $s (PARM-DATE=$PARM_DATE)"
  # COB_LS_FIXED=1 keeps LINE SEQUENTIAL output records at their full fixed length
  # (350 / 300 bytes) instead of trimming trailing spaces.
  COB_LS_FIXED=1 \
  DD_TCATBALF="$in/tcatbal.txt" \
  DD_XREFFILE="$in/cardxref.txt" \
  DD_DISCGRP="$in/discgrp.txt" \
  DD_ACCTFILE="$in/acctdata.txt" \
  DD_ACCTOUT="$out/acctdata.txt" \
  DD_TRANSACT="$out/systran.txt" \
    "$BUILD/cbact04" "$PARM_DATE" > "$out/run.log"
  grep -q 'END OF EXECUTION OF PROGRAM CBACT04C' "$out/run.log" || { cat "$out/run.log" >&2; exit 1; }
  check_len "$in/tcatbal.txt" 50
  check_len "$in/discgrp.txt" 50
  check_len "$in/acctdata.txt" 300
  check_len "$in/cardxref.txt" 50
  check_len "$out/systran.txt" 350
  check_len "$out/acctdata.txt" 300
  echo "  $(wc -l < "$out/systran.txt") transactions, $(wc -l < "$out/acctdata.txt") accounts -> $out"
done
