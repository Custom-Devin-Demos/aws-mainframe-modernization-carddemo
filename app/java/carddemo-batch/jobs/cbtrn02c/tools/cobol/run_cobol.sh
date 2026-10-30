#!/usr/bin/env bash
# Runs the original app/cbl/CBTRN02C.cbl under GnuCOBOL 3.1.2 (BDB indexed files) and writes the
# canonical expected outputs of one parity scenario.
#
#   tools/cobol/run_cobol.sh <input-dir> <expected-dir>
#
# <input-dir> holds the legacy ASCII unloads: dailytran.txt (DALYTRAN), cardxref.txt (XREFFILE),
# acctdata.txt (ACCTFILE), tcatbal.txt (TCATBALF). <expected-dir> receives posted_transactions.csv,
# daily_rejects.csv, account_master.csv, tran_cat_balance.csv, sysout.txt and return_code.txt.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../../../../../.." && pwd)"
in="$(cd "$1" && pwd)"
mkdir -p "$2"
out="$(cd "$2" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# 1. Build: the unmodified program + CEE3ABD stub, EBCDIC-style sign overpunch as in the samples.
cobc -x -fsign=EBCDIC -I "$repo/app/cpy" -o "$work/cbtrn02c" "$repo/app/cbl/CBTRN02C.cbl" "$here/CEE3ABD.cbl"
cobc -x -free -o "$work/idxutil" "$here/IDXUTIL.cbl"
gcc -shared -fPIC -O2 -o "$work/fixedclock.so" "$here/fixedclock.c"

# 2. Inputs: ASCII lines -> fixed-length records; KSDS stand-ins loaded through IDXUTIL.
python3 "$here/cobol_files.py" to-fixed "$in/dailytran.txt" 350 "$work/dalytran.dat"
python3 "$here/cobol_files.py" to-fixed "$in/cardxref.txt" 50 "$work/xref.seq"
python3 "$here/cobol_files.py" to-fixed "$in/acctdata.txt" 300 "$work/acct.seq"
python3 "$here/cobol_files.py" to-fixed "$in/tcatbal.txt" 50 "$work/tcat.seq"
export DD_XREFFILE="$work/xref.idx" DD_ACCTFILE="$work/acct.idx" DD_TCATBALF="$work/tcat.idx" DD_TRANFILE="$work/tran.idx"
DD_SEQFILE="$work/xref.seq" "$work/idxutil" LOAD XREF
DD_SEQFILE="$work/acct.seq" "$work/idxutil" LOAD ACCT
DD_SEQFILE="$work/tcat.seq" "$work/idxutil" LOAD TCAT

# 3. Run with the JobHarness clock (2022-07-06T00:00:00Z, zero hundredths).
export DD_DALYTRAN="$work/dalytran.dat" DD_DALYREJS="$work/dalyrejs.dat"
set +e
TZ=UTC LD_PRELOAD="$work/fixedclock.so" "$work/cbtrn02c" > "$out/sysout.txt"
rc=$?
set -e
echo "$rc" > "$out/return_code.txt"
echo "CBTRN02C RC=$rc"
if [ "$rc" -ne 0 ] && [ "$rc" -ne 4 ]; then
  # Abend: no datasets are published (the Java job publishes nothing either).
  rm -f "$out"/*.csv
  exit 0
fi

# 4. Outputs: unload the KSDSs in key order and convert everything to canonical CSV.
DD_SEQFILE="$work/tran.seq" "$work/idxutil" UNLOAD TRAN
DD_SEQFILE="$work/acct.out" "$work/idxutil" UNLOAD ACCT
DD_SEQFILE="$work/tcat.out" "$work/idxutil" UNLOAD TCAT
[ -f "$work/dalyrejs.dat" ] || : > "$work/dalyrejs.dat"
python3 "$here/cobol_files.py" to-csv posted_transactions "$work/tran.seq" "$out/posted_transactions.csv"
python3 "$here/cobol_files.py" to-csv daily_rejects "$work/dalyrejs.dat" "$out/daily_rejects.csv"
python3 "$here/cobol_files.py" to-csv account_master "$work/acct.out" "$out/account_master.csv"
python3 "$here/cobol_files.py" to-csv tran_cat_balance "$work/tcat.out" "$out/tran_cat_balance.csv"
