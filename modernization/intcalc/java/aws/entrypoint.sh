#!/usr/bin/env bash
# Stages inputs from S3, runs the job, and publishes outputs to S3.
#   INPUT_S3_URI   s3://bucket/prefix/ containing tcatbal.txt cardxref.txt acctdata.txt discgrp.txt
#   OUTPUT_S3_URI  s3://bucket/prefix/ receiving transact.dat and acctdata.dat
#   PARM_DATE      JCL PARM, e.g. 2022071800 (default)
#   PROCESSING_TS  optional ISO-8601 local timestamp; defaults to now
#   UPDATE_LAST_ACCOUNT=true to apply the fix for CBACT04C's skipped last-account update
# Without the S3 variables it runs against /data/in -> /data/out (e.g. a mounted EFS volume).
set -euo pipefail
IN=/data/in
OUT=/data/out
mkdir -p "$IN" "$OUT"
if [[ -n "${INPUT_S3_URI:-}" ]]; then
  aws s3 cp --recursive "$INPUT_S3_URI" "$IN"
fi
args=(--input-dir "$IN" --output-dir "$OUT" --parm-date "${PARM_DATE:-2022071800}")
[[ -n "${PROCESSING_TS:-}" ]] && args+=(--processing-ts "$PROCESSING_TS")
[[ "${UPDATE_LAST_ACCOUNT:-false}" == "true" ]] && args+=(--update-last-account)
java ${JAVA_OPTS:-} -jar /app/intcalc.jar "${args[@]}"
if [[ -n "${OUTPUT_S3_URI:-}" ]]; then
  aws s3 cp --recursive "$OUT" "$OUTPUT_S3_URI"
fi
