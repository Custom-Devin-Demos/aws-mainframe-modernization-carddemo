# CBTRN03C — Transaction detail report (TRANREPT)

Java 17 replacement for `app/cbl/CBTRN03C.cbl` and JCL `app/jcl/TRANREPT.jcl` (STEP05R REPRO + SORT folded
in as `TransactSortStep`). Business rules: `docs/modernization/business-rules/CBTRN03C.md`.

```bash
java -jar target/cbtrn03c-0.1.0-SNAPSHOT.jar \
  --input TRANFILE=transact.txt --input CARDXREF=cardxref.txt --input TRANTYPE=trantype.txt \
  --input TRANCATG=trancatg.txt --input DATEPARM=dateparm.txt \
  --output-root out --run-date 2022-07-06 --run-id local
```

* `TRANFILE` is the TRANSACT KSDS unload (any order). DATEPARM is `start-date(10) filler(1) end-date(10)`;
  without the DD, `--param START-DATE=yyyy-mm-dd --param END-DATE=yyyy-mm-dd` supply it. The SORT INCLUDE
  uses `--param SORT-START-DATE/SORT-END-DATE`, defaulting to the DATEPARM dates.
* Output: `transaction_report` (CSV) + `_schema.json`, contract in `src/main/resources/schemas/`.
* Fixtures: `src/test/resources/fixtures/README.md`; COBOL harness: `tools/cobol/README.md`.
* Deploy templates (not deployed): `deploy/batch-job-definition.json`, `deploy/TRANREPT.asl.json`.
