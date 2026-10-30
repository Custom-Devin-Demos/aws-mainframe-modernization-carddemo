#!/usr/bin/env bash
# Generates expected/ for COBTUPDT parity fixtures by running the original COBTUPDT.cbl under GnuCOBOL.
#   tools/cobol/run_scenario.sh                 # every scenario under src/test/resources/fixtures
#   tools/cobol/run_scenario.sh sample-data     # one scenario
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
MODULE="$(cd "$HERE/../.." && pwd)"
REPO="$(cd "$MODULE/../../../../.." && pwd)"
SRC="$REPO/app/app-transaction-type-db2/cbl/COBTUPDT.cbl"
SAMPLE="$REPO/app/data/ASCII"
FIXTURES="$MODULE/src/test/resources/fixtures"
BUILD="$MODULE/target/cobol-harness"

mkdir -p "$BUILD/bin"
python3 "$HERE/precompile.py" "$SRC" "$BUILD/bin/COBTUPDT.cob"
(cd "$BUILD/bin" && cobc -x -o cobtupdt COBTUPDT.cob "$HERE/TTDBSHIM.cbl" \
  && cobc -x -o ttload "$HERE/TTLOAD.cbl" && cobc -x -o ttunld "$HERE/TTUNLD.cbl")

if [ $# -eq 0 ]; then
  set -- $(cd "$FIXTURES" && for d in */input; do dirname "$d"; done)
fi

for scenario in "$@"; do
  in="$FIXTURES/$scenario/input"
  out="$FIXTURES/$scenario/expected"
  work="$BUILD/$scenario"
  rm -rf "$work" && mkdir -p "$work" "$out"
  trantype="$in/TRANTYPE.txt"; [ -f "$trantype" ] || trantype="$SAMPLE/trantype.txt"
  trancatg="$in/TRANCATG.txt"; [ -f "$trancatg" ] || trancatg="$SAMPLE/trancatg.txt"

  # ASCII unloads -> fixed-length records, as the FB datasets would hold them.
  python3 "$HERE/to_fixed.py" "$in/INPFILE.txt" 53 keep-blank > "$work/INPFILE.dat"
  python3 "$HERE/to_fixed.py" "$trantype" 60 skip-blank > "$work/TRANTYPE.dat"
  python3 "$HERE/to_fixed.py" "$trancatg" 60 skip-blank > "$work/TRANCATG.dat"

  export DD_TTKSDS="$work/TRANTYPE.ksds" DD_TCKSDS="$work/TRANCATG.ksds"
  DD_TTIN="$work/TRANTYPE.dat" DD_TCIN="$work/TRANCATG.dat" "$BUILD/bin/ttload"

  set +e
  DD_INPFILE="$work/INPFILE.dat" DD_SQLTRACE="$work/sqltrace.txt" "$BUILD/bin/cobtupdt" > "$work/sysout.raw"
  rc=$?
  set -e
  [ -f "$work/sqltrace.txt" ] || : > "$work/sqltrace.txt"

  DD_TTOUT="$work/unload.txt" "$BUILD/bin/ttunld"

  python3 "$HERE/to_canonical.py" --inpfile "$work/INPFILE.dat" --sysout "$work/sysout.raw" \
    --sqltrace "$work/sqltrace.txt" --unload "$work/unload.txt" --rc "$rc" --out "$out"
  echo "$scenario: RC=$rc -> $out"
done
