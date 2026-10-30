#!/usr/bin/env bash
# Regenerates the COBOL-derived expected fixtures for CBACT02C by running the original
# app/cbl/CBACT02C.cbl under GnuCOBOL 3.1.2 (BDB indexed files). See README.md.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE="$(cd "$HERE/../.." && pwd)"
APP="$(cd "$MODULE/../../../.." && pwd)"
FIXTURES="$MODULE/src/test/resources/fixtures"
WORK="${WORK:-$MODULE/target/cobol}"

# Scenarios run under GnuCOBOL: <scenario> <CARDFILE unload, or "-" for a missing file>
SCENARIOS=(
  "sample-data $APP/data/ASCII/carddata.txt"
  "key-order $FIXTURES/key-order/input/carddata.txt"
  "unload-format $FIXTURES/unload-format/input/carddata.txt"
  "raw-content $FIXTURES/raw-content/input/carddata.txt"
  "empty-file $FIXTURES/empty-file/input/carddata.txt"
  "missing-file -"
)

rm -rf "$WORK" && mkdir -p "$WORK/bin"
cobc --version | head -1
cobc -x -fsign=EBCDIC -I "$APP/cpy" -o "$WORK/bin/cbact02c" "$APP/cbl/CBACT02C.cbl" "$HERE/CEE3ABD.cbl"
cobc -x -o "$WORK/bin/loadcard" "$HERE/LOADCARD.cbl"

# 9910-DISPLAY-IO-STATUS for statuses the sample data cannot produce (paragraph extracted verbatim).
awk '/^       01  IO-STATUS\./,/IO-STATUS-0403/' "$APP/cbl/CBACT02C.cbl" >"$WORK/WS9910.cpy"
awk '/^       9910-DISPLAY-IO-STATUS\./{p=1} p{print} p&&/^           EXIT\./{exit}' "$APP/cbl/CBACT02C.cbl" >"$WORK/P9910.cpy"
cobc -x -I "$WORK" -o "$WORK/bin/iostat" "$HERE/IOSTAT.cbl"
mkdir -p "$FIXTURES/io-status/expected"
{ echo "io_status,display_line"; "$WORK/bin/iostat" | sed 's/ *$//'; } >"$FIXTURES/io-status/expected/ascii.csv"
python3 "$HERE/ebcdic_io_status.py" "$FIXTURES/io-status/expected/ascii.csv" "$FIXTURES/io-status/expected/ebcdic.csv"
echo "io-status: $(($(wc -l <"$FIXTURES/io-status/expected/ascii.csv") - 1)) statuses"

for entry in "${SCENARIOS[@]}"; do
  read -r name input <<<"$entry"
  dir="$WORK/$name" && mkdir -p "$dir"
  ksds="$dir/CARDFILE.ksds"
  if [[ "$input" != "-" ]]; then
    DD_CARDIN="$input" DD_CARDFILE="$ksds" "$WORK/bin/loadcard"
  fi
  set +e
  DD_CARDFILE="$ksds" "$WORK/bin/cbact02c" >"$dir/sysout.raw"
  rc=$?
  set -e
  echo "$name: RC=$rc"
  out="$FIXTURES/$name/expected"
  rm -f "$out"/sysout.csv "$out"/card_listing.csv "$out"/sysout.txt
  python3 "$HERE/to_canonical.py" "$dir/sysout.raw" "$out"
done
