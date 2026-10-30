# CBACT03C — Card cross-reference listing (READXREF)

Java 17 migration of `app/cbl/CBACT03C.cbl` (JCL `READXREF`, step `STEP05`). Business rules:
`docs/modernization/business-rules/CBACT03C.md`.

* Job: `com.carddemo.batch.cbact03c.Cbact03cJob` (`programId` `CBACT03C`), registered via `ServiceLoader`.
* Input: DD `XREFFILE` — the CARDXREF unload (CVACT03Y, 50-byte fixed-width ASCII).
* Outputs: `xref_listing` (Parquet, records in key order) and `sysout` (CSV `line_no,line_text`);
  schemas in `src/main/resources/schemas/`.
* RC 0 on success; abend U999 (`AbendException`, launcher exit 99) on any XREFFILE open/read/close error.

```bash
cd app/java/carddemo-batch
./mvnw -B verify -pl jobs/cbact03c -am
java -jar jobs/cbact03c/target/cbact03c-job.jar --input XREFFILE=../../data/ASCII/cardxref.txt \
  --output-root /tmp/carddemo --run-date 2022-07-06
```

* `deploy/cbact03c-job-definition.json` — AWS Batch job definition; `deploy/READXREF.asl.json` — Step Functions state machine. Templates only, nothing is deployed.
* `tools/cobol/` — GnuCOBOL harness that produced the expected fixtures (see its README).
* `src/test/resources/fixtures/README.md` — how every expected file was derived.
