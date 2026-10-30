#!/usr/bin/env bash
# Runs the GnuCOBOL build of CBTRN01C on one scenario.
#   run_scenario.sh OUTDIR [DD=ascii-file ...]
# DDs: DALYTRAN CUSTFILE XREFFILE CARDFILE ACCTFILE TRANFILE. A DD that is not given points at a
# file that does not exist, so its OPEN fails (file status 35), like a missing DD/dataset.
# Writes OUTDIR/sysout.txt (stdout, byte-exact), OUTDIR/stderr.txt and OUTDIR/exit_code.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
BUILD="${BUILD:-$HERE/build}"
OUT="$1"; shift
mkdir -p "$OUT"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

declare -A RECLEN=([CUSTFILE]=500 [XREFFILE]=50 [CARDFILE]=150 [ACCTFILE]=300 [TRANFILE]=350)
for dd in DALYTRAN CUSTFILE XREFFILE CARDFILE ACCTFILE TRANFILE; do
  export "DD_$dd=$WORK/missing-$dd"
done
for arg in "$@"; do
  dd="${arg%%=*}"; src="${arg#*=}"
  if [[ "$dd" == DALYTRAN ]]; then
    python3 "$HERE/to_fixed.py" 350 "$src" "$WORK/dalytran.dat"
    export DD_DALYTRAN="$WORK/dalytran.dat"
  else
    [[ -n "${RECLEN[$dd]:-}" ]] || { echo "unknown DD $dd" >&2; exit 2; }
    lower="$(echo "$dd" | tr 'A-Z' 'a-z')"
    LOADIN="$src" LOADOUT="$WORK/$lower.dat" "$BUILD/load_$lower" 2>>"$OUT/stderr.txt"
    export "DD_$dd=$WORK/$lower.dat"
  fi
done

set +e
"$BUILD/cbtrn01c" >"$OUT/sysout.txt" 2>>"$OUT/stderr.txt"
echo $? >"$OUT/exit_code"
set -e
echo "$OUT: exit $(cat "$OUT/exit_code")" >&2
