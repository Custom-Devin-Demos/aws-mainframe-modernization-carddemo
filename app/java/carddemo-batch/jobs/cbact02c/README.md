# CBACT02C — Card file read (READCARD)

Java 17 migration of `app/cbl/CBACT02C.cbl` (JCL `app/jcl/READCARD.jcl`, STEP05): reads the card master
KSDS (DD `CARDFILE`, copybook CVACT02Y) in key order and DISPLAYs every card record.

- Rules and traceability: `docs/modernization/business-rules/CBACT02C.md`
- Outputs: `card_listing` (Parquet) and `sysout` (CSV); contracts in `src/main/resources/schemas/`
- Deploy templates (not deployed): `deploy/cbact02c-job-definition.json`, `deploy/READCARD.asl.json`
- Fixtures: `src/test/resources/fixtures/README.md`; COBOL harness: `tools/cobol/README.md`

```bash
cd app/java/carddemo-batch
./mvnw -B verify -pl jobs/cbact02c -am
java -jar jobs/cbact02c/target/cbact02c-job.jar --input CARDFILE=../../data/ASCII/carddata.txt \
  --output-root /tmp/carddemo --run-date 2022-07-06 --run-id local
```

| Class | Role |
|-------|------|
| `Cbact02cJob` | `BatchJob`; one method per COBOL paragraph |
| `CardFile` / `KsdsCardFile` | CARDFILE OPEN/READ/CLOSE with COBOL file statuses, backed by `KeyedFile` |
| `IoStatus` | `9910-DISPLAY-IO-STATUS` formatting (EBCDIC code page) |
| `DisplayLog` | DISPLAY to SYSOUT, kept for the `sysout` dataset |
