#!/usr/bin/env bash
# Regenerates every expected fixture of the CBACT01C parity tests from the original COBOL program
# compiled with GnuCOBOL 3.1.2. Never runs the Java job. See README.md.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
MODULE="$(cd "$HERE/../.." && pwd)"
REPO="$(cd "$MODULE/../../../../.." && pwd)"
FIX="$MODULE/src/test/resources/fixtures"
WORK="${WORK:-$HERE/target/work}"
rm -rf "$WORK" && mkdir -p "$WORK"

python3 "$HERE/make_edge_inputs.py" "$FIX"

run() { # <scenario> <input|MISSING>
  "$HERE/run_cbact01c.sh" "$2" "$WORK/$1"
  python3 "$HERE/to_canonical.py" "$WORK/$1" "$FIX/$1/expected"
}

run sample-data "$REPO/app/data/ASCII/acctdata.txt"
for s in cyc-debit-carryover field-values-and-key-order reissue-date-formats empty-acctfile; do
  run "$s" "$FIX/$s/input/acctdata.txt"
done
run acctfile-missing MISSING

# OPEN failures: the DD points below a regular file, so the OPEN OUTPUT fails (ENOTDIR).
touch "$WORK/not-a-directory"
for dd in OUTFILE ARRYFILE VBRCFILE; do
  s="$(echo "$dd" | tr 'A-Z' 'a-z')-open-error"
  env "CB_$dd=$WORK/not-a-directory/$dd" "$HERE/run_cbact01c.sh" "$FIX/$s/input/acctdata.txt" "$WORK/$s"
  rm -f "$WORK/$s/$dd"
  python3 "$HERE/to_canonical.py" "$WORK/$s" "$FIX/$s/expected"
done

# READ failure: GnuCOBOL reads the non-numeric record without error (the FD is PIC X), so only
# the part before the failing record comes from COBOL; the error tail follows paragraph
# 1000-ACCTFILE-GET-NEXT / 9910-DISPLAY-IO-STATUS / 9999-ABEND-PROGRAM by hand.
s=acctfile-read-error
head -1 "$FIX/$s/input/acctdata.txt" > "$WORK/$s.prefix.txt"
"$HERE/run_cbact01c.sh" "$WORK/$s.prefix.txt" "$WORK/$s"
grep -v '^END OF EXECUTION OF PROGRAM CBACT01C$' "$WORK/$s/sysout.txt" > "$WORK/$s/sysout.tmp"
printf '%s\n' 'ERROR READING ACCOUNT FILE' 'FILE STATUS IS: NNNN0030' 'ABENDING PROGRAM' >> "$WORK/$s/sysout.tmp"
mv "$WORK/$s/sysout.tmp" "$WORK/$s/sysout.txt"
echo 'CEE3ABD-STUB ABCODE=+0000000999 TIMING=+0000000000' > "$WORK/$s/stderr.txt"
echo 99 > "$WORK/$s/rc.txt"
python3 "$HERE/to_canonical.py" "$WORK/$s" "$FIX/$s/expected"
echo "fixtures regenerated under $FIX"
