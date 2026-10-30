#!/usr/bin/env bash
# End-to-end parity run: COBOL (GnuCOBOL) vs Java on the same inputs, then the
# comparison notebook executed locally with PySpark.
# Prereqs: gnucobol (cobc 3.x, BDB indexed handler), JDK 17 + Maven, python3 with
# pyspark, nbconvert, ipykernel.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
PARM_DATE="2022071800"
COBOL_TS="2022/07/18 01:02:03"      # COB_CURRENT_DATE format
JAVA_TS="2022-07-18T01:02:03"       # ISO-8601 LocalDateTime

python3 "$HERE/data/generate_sample_data.py"
(cd "$HERE/../java" && mvn -q -B package)
JAR="$HERE/../java/target/intcalc.jar"

declare -A INPUTS=(
  [sample]="$HERE/data/sample"
  [carddemo-seed]="$REPO/app/data/ASCII"
)
for ds in sample carddemo-seed; do
  out="$HERE/output/$ds"
  rm -rf "$out"; mkdir -p "$out/input" "$out/java"
  for f in tcatbal cardxref acctdata discgrp; do
    tr -d '\r' < "${INPUTS[$ds]}/$f.txt" > "$out/input/$f.txt"
  done
  echo "== [$ds] COBOL CBACT04C"
  "$HERE/cobol/run_cobol.sh" "$out/input" "$out" "$PARM_DATE" "$COBOL_TS"
  rm -rf "$out/.cobol-build"
  echo "== [$ds] Java intcalc"
  java -jar "$JAR" --input-dir "$out/input" --output-dir "$out/java" \
       --parm-date "$PARM_DATE" --processing-ts "$JAVA_TS" | tee "$out/java/sysout.txt"
done

echo "== Parity notebook (LOCAL PySpark run)"
cd "$HERE/notebooks"
INTCALC_PARITY_OUTPUT="$HERE/output" jupyter nbconvert --to notebook --execute \
  intcalc_parity.ipynb --output intcalc_parity.local_run.ipynb
jupyter nbconvert --to html intcalc_parity.local_run.ipynb
grep -o "OVERALL RESULT: [A-Z]*" intcalc_parity.local_run.ipynb | head -1
