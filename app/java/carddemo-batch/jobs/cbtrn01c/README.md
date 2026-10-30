# CBTRN01C — Daily transaction verification

Java 17 migration of `app/cbl/CBTRN01C.cbl`: `com.carddemo.batch.cbtrn01c.Cbtrn01cJob` (program id `CBTRN01C`).
Business rules: `docs/modernization/business-rules/CBTRN01C.md`.

- Inputs (DD): `DALYTRAN`, `XREFFILE`, `ACCTFILE` (read); `CUSTFILE`, `CARDFILE`, `TRANFILE` (opened only, required).
- Outputs: `daily_tran_verification` (Parquet), `sysout` (CSV); schemas in `src/main/resources/schemas/`.
- RC 0 on normal end; OPEN/READ failures throw `AbendException(999)` (CEE3ABD).
- Deploy templates: `deploy/cbtrn01c-job-definition.json`, `deploy/DALYVRFY.asl.json` (not deployed).
- Parity: `src/test/resources/fixtures/` from GnuCOBOL runs of the original program (`tools/cobol/`).
