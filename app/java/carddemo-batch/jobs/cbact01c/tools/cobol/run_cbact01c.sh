#!/usr/bin/env bash
# Compiles the original app/cbl/CBACT01C.cbl with GnuCOBOL 3.1.2 and runs it on one input.
#   run_cbact01c.sh <acctdata.txt | MISSING> <work-dir>
# CB_OUTFILE / CB_ARRYFILE / CB_VBRCFILE override an output path (used to force an OPEN failure).
# Leaves OUTFILE, ARRYFILE, VBRCFILE (raw GnuCOBOL record-sequential files), sysout.txt (stdout),
# stderr.txt and rc.txt in <work-dir>.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../../../../.." && pwd)"
INPUT="$1"
WORK="$(mkdir -p "$2" && cd "$2" && pwd)"
BIN="$HERE/target"
mkdir -p "$BIN"
COBFLAGS=(-fsign=EBCDIC -fno-pretty-display -fstatic-call)
if [[ ! -x "$BIN/cbact01c" || "$REPO/app/cbl/CBACT01C.cbl" -nt "$BIN/cbact01c" || "$HERE/COBDATFT.cbl" -nt "$BIN/cbact01c" ]]; then
  cobc -x "${COBFLAGS[@]}" -I "$REPO/app/cpy" -o "$BIN/cbact01c" \
    "$REPO/app/cbl/CBACT01C.cbl" "$HERE/COBDATFT.cbl" "$HERE/CEE3ABD.cbl"
  cobc -x "${COBFLAGS[@]}" -o "$BIN/loadacct" "$HERE/LOADACCT.cbl"
fi
rm -f "$WORK"/{ACCTFILE,OUTFILE,ARRYFILE,VBRCFILE,sysout.txt,stderr.txt,rc.txt}*
if [[ "$INPUT" == "MISSING" ]]; then
  export ACCTFILE="$WORK/ACCTFILE.does-not-exist"
else
  ACCTIN="$INPUT" ACCTFILE="$WORK/ACCTFILE" "$BIN/loadacct"
  export ACCTFILE="$WORK/ACCTFILE"
fi
export OUTFILE="${CB_OUTFILE:-$WORK/OUTFILE}" ARRYFILE="${CB_ARRYFILE:-$WORK/ARRYFILE}" VBRCFILE="${CB_VBRCFILE:-$WORK/VBRCFILE}"
set +e
"$BIN/cbact01c" > "$WORK/sysout.txt" 2> "$WORK/stderr.txt"
echo $? > "$WORK/rc.txt"
set -e
echo "rc=$(cat "$WORK/rc.txt") $(wc -l < "$WORK/sysout.txt") sysout lines"
