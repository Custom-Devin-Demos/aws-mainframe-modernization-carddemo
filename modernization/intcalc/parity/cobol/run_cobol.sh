#!/usr/bin/env bash
# Compile the unmodified CBACT04C with GnuCOBOL and run it against a
# line-sequential data set.
#   usage: run_cobol.sh <input-dir> <output-dir> [parm-date] [processing-ts]
# processing-ts ("YYYY/MM/DD HH:MM:SS") pins FUNCTION CURRENT-DATE via
# COB_CURRENT_DATE so TRAN-ORIG-TS / TRAN-PROC-TS are reproducible.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
IN="$(cd "$1" && pwd)"
mkdir -p "$2"
OUT="$(cd "$2" && pwd)"
PARM_DATE="${3:-2022071800}"
PROC_TS="${4:-2022/07/18 01:02:03}"
BUILD="$OUT/.cobol-build"
rm -rf "$BUILD"; mkdir -p "$BUILD"

COBFLAGS=(-fsign=EBCDIC -I "$REPO/app/cpy")
cobc -x "${COBFLAGS[@]}" -o "$BUILD/loadidx"  "$HERE/LOADIDX.cbl"
cobc -x "${COBFLAGS[@]}" -o "$BUILD/unldacct" "$HERE/UNLDACCT.cbl"
cobc -x "${COBFLAGS[@]}" -o "$BUILD/intcalc" \
     "$HERE/INTCDRV.cbl" "$REPO/app/cbl/CBACT04C.cbl" "$HERE/CEE3ABD.cbl"

export DD_TCATIN="$IN/tcatbal.txt" DD_XREFIN="$IN/cardxref.txt" \
       DD_ACCTIN="$IN/acctdata.txt" DD_DISCIN="$IN/discgrp.txt" \
       DD_TCATBALF="$BUILD/TCATBALF" DD_XREFFILE="$BUILD/XREFFILE" \
       DD_ACCTFILE="$BUILD/ACCTFILE" DD_DISCGRP="$BUILD/DISCGRP" \
       DD_TRANSACT="$OUT/cobol_transact.dat" DD_ACCTOUT="$OUT/cobol_acctdata.dat"
"$BUILD/loadidx"
COB_CURRENT_DATE="$PROC_TS" "$BUILD/intcalc" "$PARM_DATE" > "$OUT/cobol_sysout.txt"
"$BUILD/unldacct"
echo "CBACT04C RC=0; outputs in $OUT"
