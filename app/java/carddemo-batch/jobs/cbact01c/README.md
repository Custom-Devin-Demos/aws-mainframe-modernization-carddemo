# CBACT01C — Account file read and reformat (READACCT)

Java 17 port of `app/cbl/CBACT01C.cbl` (JCL `app/jcl/READACCT.jcl`, step `STEP05`).
Business rules: `docs/modernization/business-rules/CBACT01C.md`.

| DD | Java | Format |
|----|------|--------|
| `ACCTFILE` (KSDS, CVACT01Y) | `ctx.input("ACCTFILE")` | ASCII unload, 300-byte lines |
| `OUTFILE` (FB 107) | `account_out` | Parquet |
| `ARRYFILE` (FB 110) | `account_array` | Parquet |
| `VBRCFILE` (VB) | `account_vbrc` | Parquet |
| `SYSOUT` | `sysout` (+ stdout) | CSV |

* Code: `src/main/java/com/carddemo/batch/cbact01c/` (`Cbact01cJob`, one method per COBOL paragraph;
  `Cobdatft`/`Codatecn` port the assembler date routine; `Cbact01cSchemas`).
* Schema contracts: `src/main/resources/schemas/*.schema.json`.
* Parity tests and fixtures: `src/test/`, fixture derivation in `src/test/resources/fixtures/README.md`.
* GnuCOBOL harness that produced the fixtures: `tools/cobol/`.
* Deploy templates (not deployed): `deploy/batch-job-definition.json`, `deploy/READACCT.asl.json`.

Run locally:

```
java -jar target/cbact01c-job.jar --input ACCTFILE=../../../../data/ASCII/acctdata.txt \
     --output-root /tmp/carddemo --run-date 2022-07-06 --run-id local
```
