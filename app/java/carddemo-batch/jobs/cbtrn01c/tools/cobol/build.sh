#!/usr/bin/env bash
# Compiles the unmodified app/cbl/CBTRN01C.cbl plus shims under GnuCOBOL 3.1.2 into $BUILD (default ./build).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../../../../.." && pwd)"
BUILD="${BUILD:-$HERE/build}"
mkdir -p "$BUILD"
cobc --version | head -1 >&2

# -fsign=EBCDIC: zoned signs as overpunch {, A-I, }, J-R (matches app/data/ASCII).
# -fstatic-call: CALL 'CEE3ABD' binds to the stub linked into the executable.
cobc -x -fsign=EBCDIC -fstatic-call -I "$REPO/app/cpy" -o "$BUILD/cbtrn01c" \
  "$REPO/app/cbl/CBTRN01C.cbl" "$HERE/CEE3ABD.cbl"

# One loader per KSDS: name reclen keylen (record lengths from the program's FDs).
while read -r name reclen keylen; do
  sed -e "s/@RECLEN@/$reclen/g" -e "s/@KEYLEN@/$keylen/g" -e "s/@DATALEN@/$((reclen - keylen))/g" \
    "$HERE/LOADKSDS.cbl.tmpl" > "$BUILD/load_$name.cbl"
  cobc -x -o "$BUILD/load_$name" "$BUILD/load_$name.cbl"
done <<'LIST'
custfile 500 9
xreffile 50 16
cardfile 150 16
acctfile 300 11
tranfile 350 16
LIST
echo "built into $BUILD" >&2
