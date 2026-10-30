#!/usr/bin/env bash
# Compiles the unmodified app/cbl/CBTRN03C.cbl plus the harness shims with GnuCOBOL 3.1.2 (BDB).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../../../../../.." && pwd)"
mkdir -p "$here/target"
cobc -x -fsign=EBCDIC -I "$repo/app/cpy" -o "$here/target/CBTRN03C" "$repo/app/cbl/CBTRN03C.cbl" "$here/CEE3ABD.cbl"
cobc -x -o "$here/target/LOADKSDS" "$here/LOADKSDS.cbl"
echo "built $here/target/CBTRN03C and LOADKSDS"
