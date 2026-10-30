# INTCALC → Java batch job

Java 17 port of the CardDemo interest calculation job (`app/jcl/INTCALC.jcl` → `app/cbl/CBACT04C.cbl`), with a parity harness that runs the original COBOL under GnuCOBOL against the same data.

* Business rules, with COBOL paragraph → Java → test mapping: [BUSINESS_RULES.md](BUSINESS_RULES.md)
* Java job: [`java/`](java) (Maven, no runtime dependencies, JUnit 5 tests)
* AWS packaging: [`java/Dockerfile`](java/Dockerfile), [`java/aws/entrypoint.sh`](java/aws/entrypoint.sh) (S3 in/out), [`java/aws/batch-job-definition.json`](java/aws/batch-job-definition.json) (AWS Batch on Fargate, placeholders for account/bucket/roles)
* Parity harness: [`parity/`](parity)

## Build and test

```bash
cd java
mvn -B package                      # runs the unit tests, builds target/intcalc.jar
java -jar target/intcalc.jar --input-dir <dir> --output-dir <dir> \
     --parm-date 2022071800 [--processing-ts 2022-07-18T01:02:03] [--update-last-account]
```

Exit codes: 0 OK, 8 usage error, 16 abend (the case where CBACT04C would call `CEE3ABD`).

## Parity run

```bash
parity/run_parity.sh
```

This script:

1. Regenerates `parity/data/sample/` with `parity/data/generate_sample_data.py`. The data is a hand-built edge-case set covering every rule.
2. For both data sets (`sample` and `carddemo-seed`, which is the repo's `app/data/ASCII` extracts):
   * Compiles the **unmodified** `app/cbl/CBACT04C.cbl` with GnuCOBOL 3.1 (`-fsign=EBCDIC`) using the harness in `parity/cobol/`:
     * `LOADIDX` stands in for IDCAMS REPRO and builds the indexed files.
     * `INTCDRV` passes the JCL PARM block.
     * `CEE3ABD` is the LE abend stub.
     * `UNLDACCT` unloads the rewritten ACCTFILE.
   * Pins the clock with `COB_CURRENT_DATE` and runs the program.
   * Runs the Java jar on the same inputs.
3. Runs `parity/notebooks/intcalc_parity.ipynb` **locally with PySpark** and writes `intcalc_parity.local_run.ipynb` and `.html`.

The notebook is written for Databricks. It reads `base_path` and `datasets` widgets when `DATABRICKS_RUNTIME_VERSION` is set, and prints the run mode in its header and in the verdict line. Its outputs are:

* row counts
* interest and balance per account
* a field-level diff of transactions, keyed by TRAN-ID
* a full-record diff of the account file
* a PASS or FAIL result

Timestamps are compared to the second. GnuCOBOL takes the hundredths of a second from the wall clock even when `COB_CURRENT_DATE` is set.
