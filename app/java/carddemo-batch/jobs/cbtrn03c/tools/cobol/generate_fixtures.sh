#!/usr/bin/env bash
# Rebuilds every expected fixture from the original COBOL program (see README.md).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
fixtures="$here/../../src/test/resources/fixtures"
repo="$(cd "$here/../../../../../../.." && pwd)"
"$here/build.sh"
python3 "$here/derive_tranfile.py" "$repo/app/data/ASCII/dailytran.txt" "$fixtures/sample-data/input/tranfile.txt"
python3 "$here/make_edge_inputs.py"
for dir in "$fixtures"/*/; do
  name="$(basename "$dir")"
  echo "== $name"
  python3 "$here/run_scenario.py" "$dir/input" "$dir/expected"
  cat "$dir/expected/result.properties"
done
