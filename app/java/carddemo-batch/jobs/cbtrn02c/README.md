# CBTRN02C — Transaction posting with balance updates (POSTTRAN)

Java 17 port of `app/cbl/CBTRN02C.cbl`: `com.carddemo.batch.cbtrn02c.Cbtrn02cJob`.

* Inputs (DD): `DALYTRAN`, `XREFFILE`, `ACCTFILE`, `TCATBALF` (legacy fixed-width ASCII unloads).
* Outputs (Parquet): `posted_transactions`, `daily_rejects`, `account_master`, `tran_cat_balance`; contracts in
  `src/main/resources/schemas/`.
* RC 4 when any transaction is rejected; abend 999 (`CEE3ABD`) on file errors.
* Business rules: `docs/modernization/business-rules/CBTRN02C.md`.
* Parity fixtures: `src/test/resources/fixtures/README.md`, produced by the GnuCOBOL harness in `tools/cobol/`.
* Deploy templates: `deploy/batch-job-definition.json`, `deploy/POSTTRAN.asl.json` (not deployed).
