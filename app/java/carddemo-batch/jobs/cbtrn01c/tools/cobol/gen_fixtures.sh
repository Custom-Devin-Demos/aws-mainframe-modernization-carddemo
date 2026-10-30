#!/usr/bin/env bash
# Regenerates every GnuCOBOL-derived fixture of jobs/cbtrn01c from the original COBOL program.
#   tools/cobol/gen_fixtures.sh
# Needs GnuCOBOL 3.1.2 (sudo apt-get install -y gnucobol3) and python3.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../../../../.." && pwd)"
SAMPLE="$REPO/app/data/ASCII"
FIX="$HERE/../../src/test/resources/fixtures"
RUNS="$HERE/build/runs"

"$HERE/build.sh"
python3 "$HERE/make_edge_inputs.py" "$SAMPLE" "$FIX"
rm -rf "$RUNS"

run() {  # scenario-dir run-name DD=file...
  local expected="$1" name="$2"; shift 2
  "$HERE/run_scenario.sh" "$RUNS/$name" "$@"
  rm -rf "$expected"
  python3 "$HERE/sysout_to_expected.py" "$RUNS/$name" "$expected"
}

all_dds() {  # input-dir dalytran custfile xrefffile cardfile acctfile tranfile
  echo "DALYTRAN=$2 CUSTFILE=$3 XREFFILE=$4 CARDFILE=$5 ACCTFILE=$6 TRANFILE=$7"
}

# shellcheck disable=SC2046
run "$FIX/sample-data/expected" sample-data $(all_dds - "$SAMPLE/dailytran.txt" "$SAMPLE/custdata.txt" \
  "$SAMPLE/cardxref.txt" "$SAMPLE/carddata.txt" "$SAMPLE/acctdata.txt" "$FIX/sample-data/input/transact.txt")

for s in xref-not-found account-not-found empty-dalytran mixed-outcomes; do
  in="$FIX/$s/input"
  # shellcheck disable=SC2046
  run "$FIX/$s/expected" "$s" $(all_dds - "$in/dailytran.txt" "$in/custdata.txt" "$in/cardxref.txt" \
    "$in/carddata.txt" "$in/acctdata.txt" "$in/transact.txt")
done

# OPEN failure (file status 35) for each DD in turn: mixed-outcomes inputs with that one DD left out.
in="$FIX/mixed-outcomes/input"
for dd in DALYTRAN CUSTFILE XREFFILE CARDFILE ACCTFILE TRANFILE; do
  args=()
  for pair in DALYTRAN=dailytran CUSTFILE=custdata XREFFILE=cardxref CARDFILE=carddata ACCTFILE=acctdata TRANFILE=transact; do
    [[ "${pair%%=*}" == "$dd" ]] || args+=("${pair%%=*}=$in/${pair#*=}.txt")
  done
  run "$FIX/open-failure/$dd/expected" "open-failure-$dd" "${args[@]}"
done
echo "fixtures regenerated under $FIX" >&2
