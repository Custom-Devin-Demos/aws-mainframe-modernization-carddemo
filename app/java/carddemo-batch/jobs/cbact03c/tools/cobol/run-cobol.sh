#!/usr/bin/env bash
# Compiles app/cbl/CBACT03C.cbl under GnuCOBOL 3.1.2 (BDB indexed files), runs it on every fixture
# scenario that the original program can reproduce, and regenerates the expected fixtures.
# Usage: tools/cobol/run-cobol.sh   (from anywhere; needs cobc and python3)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
MODULE="$(cd "$HERE/../.." && pwd)"
REPO="$(cd "$MODULE/../../../../.." && pwd)"
BUILD="$MODULE/target/cobol-harness"
FIX="$MODULE/src/test/resources/fixtures"
SAMPLE="$REPO/app/data/ASCII"

rm -rf "$BUILD" && mkdir -p "$BUILD/run"
cobc --version | head -1

# The original program, unmodified; CEE3ABD is the only shim it needs (no COBDATFT call, no signed fields,
# so -fsign=EBCDIC is kept only for consistency with the other CardDemo harnesses).
cobc -x -fsign=EBCDIC -fstatic-call -I "$REPO/app/cpy" -o "$BUILD/cbact03c" "$REPO/app/cbl/CBACT03C.cbl" "$HERE/CEE3ABD.cbl"
cobc -x -o "$BUILD/xrefload" "$HERE/XREFLOAD.cbl"
python3 "$HERE/gen_iostatdrv.py" "$REPO/app/cbl/CBACT03C.cbl" "$HERE/IOSTATDRV.cbl.template" "$BUILD/IOSTATDRV.cbl"
cobc -x -o "$BUILD/iostatdrv" "$BUILD/IOSTATDRV.cbl"

# run_scenario <name> <ascii-unload or "-" for no file>
run_scenario() {
  local name="$1" unload="$2" dir="$BUILD/run/$1" out="$FIX/$1/expected"
  mkdir -p "$dir" "$out"
  local ksds="$dir/XREFFILE.dat"
  if [[ "$unload" != "-" ]]; then
    DD_XREFIN="$unload" DD_XREFFILE="$ksds" "$BUILD/xrefload" > "$dir/load.log"
  fi
  set +e
  (cd "$dir" && DD_XREFFILE="$ksds" "$BUILD/cbact03c" > stdout 2> stderr; echo $? > exit)
  set -e
  python3 "$HERE/to_canonical.py" run "$dir" "$out"
  echo "$name: exit $(cat "$dir/exit"), $(wc -l < "$dir/stdout") SYSOUT lines"
}

run_scenario sample-data "$SAMPLE/cardxref.txt"
for s in unsorted-keys unload-format raw-content empty-file; do
  run_scenario "$s" "$FIX/$s/input/cardxref.txt"
done
run_scenario open-error-missing-file -

# 9910-DISPLAY-IO-STATUS for every status in the table
DD_STATIN="$FIX/display-io-status/input/statuses.txt" "$BUILD/iostatdrv" > "$BUILD/run/iostat.stdout"
python3 "$HERE/to_canonical.py" iostat "$FIX/display-io-status/input/statuses.txt" "$BUILD/run/iostat.stdout" \
  "$FIX/display-io-status/expected/display-io-status.csv"
echo "display-io-status: $(wc -l < "$BUILD/run/iostat.stdout") lines"
