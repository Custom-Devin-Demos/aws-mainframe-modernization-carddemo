#!/usr/bin/env bash
# Runs CBSTM03A under GnuCOBOL and the Java port on the repo sample data and
# diffs the two statement files. Exits non-zero unless both are identical.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE="$(cd "$HERE/.." && pwd)"
ROOT="$(cd "$MODULE/../../.." && pwd)"
OUT="${1:-$MODULE/target/parity}"
DATA="$ROOT/app/data/ASCII"

"$HERE/run-cobol.sh" "$OUT/cobol"

(cd "$MODULE" && mvn -q -B -DskipTests package)
mkdir -p "$OUT/java"
java -jar "$MODULE"/target/creastmt-*.jar \
  --xref="$DATA/cardxref.txt" --cust="$DATA/custdata.txt" \
  --acct="$DATA/acctdata.txt" --trnx="$DATA/dailytran.txt" \
  --stmt="$OUT/java/STATEMNT.PS" --html="$OUT/java/STATEMNT.HTML"

status=0
for f in STATEMNT.PS STATEMNT.HTML; do
  echo "\$ diff cobol/$f java/$f"
  if diff "$OUT/cobol/$f" "$OUT/java/$f"; then
    echo "identical: $(wc -l < "$OUT/java/$f") records, sha256 $(sha256sum < "$OUT/java/$f" | cut -c1-16)"
  else
    status=1
  fi
done
exit $status
