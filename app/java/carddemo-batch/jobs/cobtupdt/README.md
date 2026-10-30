# COBTUPDT — Db2 transaction-type maintenance (MNTTRDB2)

Java 17 port of `app/app-transaction-type-db2/cbl/COBTUPDT.cbl` (job `MNTTRDB2`, STEP1). Business rules:
`docs/modernization/business-rules/COBTUPDT.md`.

| DD | Content |
|---|---|
| `INPFILE` | maintenance records, 53 bytes: type `A`/`U`/`D`/`*` (1), tr_type (2), description (50) |
| `TRANTYPE` | `CARDDEMO.TRANSACTION_TYPE` before the run, CVTRA03Y unload (`app/data/ASCII/trantype.txt`) |
| `TRANCATG` | `CARDDEMO.TRANSACTION_TYPE_CATEGORY`, CVTRA04Y unload (`app/data/ASCII/trancatg.txt`), for ON DELETE RESTRICT |

Outputs: `transaction_type` (Parquet, post-maintenance snapshot ordered by `tr_type`) and `maintenance_log`
(CSV, one row per INPFILE record). RC 0, or 4 when any record failed (processing always continues).

```bash
java -jar target/cobtupdt-*.jar --job COBTUPDT \
  --input INPFILE=maint.txt --input TRANTYPE=../../../../data/ASCII/trantype.txt \
  --input TRANCATG=../../../../data/ASCII/trancatg.txt --output-root /tmp/out --run-date 2022-07-06 --run-id local
```

* Parity fixtures: `src/test/resources/fixtures/README.md`; COBOL harness: `tools/cobol/README.md`.
* Deploy templates (not deployed): `deploy/batch-job-definition.json`, `deploy/MNTTRDB2.asl.json`.
