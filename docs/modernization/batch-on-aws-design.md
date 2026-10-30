# CardDemo batch on AWS: modernization design

| | |
|---|---|
| Scope | `CBACT01C`, `CBACT02C`, `CBACT03C`, `CBTRN01C`, `CBTRN02C`, `CBTRN03C` (`app/cbl`) and `COBTUPDT` (`app/app-transaction-type-db2/cbl`, job `MNTTRDB2`) |
| Out of scope | `CBACT04C`, which is migrated separately; nothing here depends on it. Online CICS programs. |
| Target | Java 17 batch jobs in containers on AWS Batch (Fargate), orchestrated by AWS Step Functions, reading from and writing to S3 |
| Outputs | Files only: Parquet for datasets, CSV for human-readable reports, each with a published schema file, ready for Databricks. **No PostgreSQL or any other relational database.** |
| Code | `app/java/carddemo-batch/` (Maven multi-module) |

## 1. Source inventory

| Program | Driving JCL (step) | Inputs (DD → copybook, organization, access) | Outputs (DD) | What it does |
|---|---|---|---|---|
| CBACT01C | `READACCT` (STEP05) | ACCTFILE → CVACT01Y, KSDS, sequential | OUTFILE (LRECL 107, FB, contains COMP-3), ARRYFILE (LRECL 110, FB, OCCURS 5 with COMP-3), VBRCFILE (VB 10–80) | Reads every account, reformats the reissue date through the `COBDATFT` assembler routine (`app/asm/COBDATFT.asm`), and writes three derived files. DISPLAYs each record. |
| CBACT02C | `READCARD` (STEP05) | CARDFILE → CVACT02Y, KSDS, sequential | SYSOUT only | Lists the card master in key order. |
| CBACT03C | `READXREF` (STEP05) | XREFFILE → CVACT03Y, KSDS, sequential | SYSOUT only | Lists the card→account cross-reference in key order. |
| CBTRN01C | none in `app/jcl` | DALYTRAN → CVTRA06Y, PS; CUSTFILE (CVCUS01Y), XREFFILE (CVACT03Y), CARDFILE (CVACT02Y), ACCTFILE (CVACT01Y), TRANFILE (CVTRA05Y): KSDS, random | SYSOUT only | Verifies each daily transaction: card → xref → account lookup. Reports cards that fail xref and accounts not found. |
| CBTRN02C | `POSTTRAN` (STEP15) | DALYTRAN (PS); XREFFILE (KSDS, random); ACCTFILE and TCATBALF (KSDS, I-O) | TRANFILE (KSDS, opened OUTPUT), DALYREJS (GDG, LRECL 430), updated ACCTFILE and TCATBALF | Validates (reasons 100 invalid card, 101 account not found, 102 overlimit, 103 after expiration). Posts valid transactions: tcatbal create/update, account balance and cycle credit/debit, transaction write with Db2-format processing timestamp. RC=4 if any rejects. |
| CBTRN03C | `TRANREPT` (STEP05R REPRO unload, STEP05R SORT by card number with a processing-date filter, then STEP10R) | TRANFILE (CVTRA05Y, sorted PS), CARDXREF, TRANTYPE (CVTRA03Y), TRANCATG (CVTRA04Y): KSDS random; DATEPARM | TRANREPT (LRECL 133, FB) | Transaction detail report with page, account and grand totals (CVTRA07Y line layouts, 20 lines per page). |
| COBTUPDT | `MNTTRDB2` (STEP1, `IKJEFT01` / DSN RUN) | INPFILE: 53-byte records `type(1) tr_type(2) desc(50)`; Db2 `CARDDEMO.TRANSACTION_TYPE` (`ddl/TRNTYPE.ddl`), FK from `TRANSACTION_TYPE_CATEGORY` (`ddl/TRNTYCAT.ddl`, ON DELETE RESTRICT) | Db2 table changes; DISPLAY messages; RC=4 on any error | Applies A(dd)/U(pdate)/D(elete) maintenance to `TRANSACTION_TYPE`. `*` lines are comments. Any other type sets an error. Errors set RC 4 and continue; they do not stop the run. |

### Sample data (`app/data/ASCII`)

| File | Copybook | Records | Notes |
|---|---|---|---|
| `acctdata.txt` | CVACT01Y (300) | 50 | Signed fields use EBCDIC-style overpunch (`{`, `A`–`I`, `}`, `J`–`R`). The `A000000000` group value sits in the `ACCT-ADDR-ZIP` columns, so `ACCT-GROUP-ID` is blank in every record. Parity follows the bytes, not the intent. |
| `carddata.txt` | CVACT02Y (150) | 50 | |
| `cardxref.txt` | CVACT03Y (50) | 50 | |
| `custdata.txt` | CVCUS01Y (500) | 50 | |
| `dailytran.txt` | CVTRA06Y (350) | 300 | All `DALYTRAN-ORIG-TS` on 2022-06-10; `DALYTRAN-PROC-TS` blank. |
| `tcatbal.txt` | CVTRA01Y (50) | 50 | Mixed CRLF/LF line endings. |
| `trancatg.txt` | CVTRA04Y (60) | 18 | CRLF. |
| `trantype.txt` | CVTRA03Y (60) | 7 | Mixed CRLF/LF. Also the initial content of Db2 `TRANSACTION_TYPE` (same columns as the `TRANEXTR` unload format). |

There is no sample `TRANSACT` file (it is produced by `CBTRN02C`) and no sample `INPFILE` for `COBTUPDT`. Those programs' parity fixtures are built from the files above (see §6.3).

## 2. Architecture

```
            S3 raw/                       AWS Step Functions (one state machine per JCL job)
  app/data/ASCII-format unloads  ──►  ┌────────────────────────────────────────────────────┐
  (acctdata.txt, dailytran.txt …)    │ Task: batch:submitJob.sync  (one task per JCL step) │
                                      └───────────────┬────────────────────────────────────┘
                                                      ▼
                                   AWS Batch job queue on Fargate  ──  ECR image per job module
                                   java -jar <program>-job.jar --input DD=s3://… --output-root s3://…
                                                      ▼
            S3 curated/{program}/{dataset}/run_date=…/run_id=…/part-00000.parquet|csv + _schema.json
                                                      ▼
                    Databricks: spark.read.schema(StructType.fromJson(_schema.json)) → Delta tables
```

### 2.1 Runtime choice: plain Java 17, not Spring Batch

These are short single-pass programs over at most a few thousand records. Spring Batch adds a
JobRepository, which is a relational database in every supported setup except the in-memory/resourceless
variants. Restartability already comes from Step Functions plus immutable `run_id` output prefixes. Plain
Java keeps cold start low on Fargate, keeps the code a readable paragraph-by-paragraph translation of the
COBOL, and keeps parity tests fast. The `BatchJob` interface (§3) is the seam if Spring Batch is wanted later.

### 2.2 AWS runtime

* **Image**: one image per job module (`Dockerfile`, `--build-arg JOB_MODULE=<module>`), `eclipse-temurin:17-jre`,
  non-root user. Entry point: `java -jar /app/job.jar` → `BatchLauncher`.
* **Compute**: AWS Batch on Fargate. One job definition per program
  (`deploy/batch-job-definition.template.json`): 1 vCPU / 2 GB, private subnets, no public IP, logs to
  CloudWatch group `/carddemo/batch` with stream prefix = program.
* **IAM**: one job role per program: `s3:GetObject` on `raw/*`, `s3:PutObject` on `curated/{program}/*` only.
  The execution role pulls from ECR and writes logs.
* **Orchestration**: one Step Functions state machine per JCL job (`READACCT`, `READCARD`, `READXREF`,
  `POSTTRAN`, `TRANREPT`, `MNTTRDB2`, plus `DALYVRFY` for `CBTRN01C`, which has no JCL in the repo). Each JCL step
  becomes a `batch:submitJob.sync` task (`deploy/state-machine.template.asl.json`). JCL `COND` checks become
  `Catch`/`Choice`. JCL utility steps (IEFBR14 deletes, IDCAMS REPRO, SORT) are not separate tasks.
  IEFBR14/DELETE is unnecessary because outputs are written under a fresh `run_id`. REPRO + SORT/INCLUDE in
  `TRANREPT` is done inside the `CBTRN03C` job, before the report logic, so the job still has a single input
  (§5, CBTRN03C).
* **Return codes**: the job exits 0 for COBOL RC 0 and 4, exits with the RC itself for RC ≥ 8, and exits 99 on
  abend (`CEE3ABD`). The RC is always written to `_manifest.json` and logged as `CARDDEMO_RETURN_CODE=n`,
  so a state machine can branch on RC 4 (for example, alert on rejects) without failing the execution.
* **Scheduling**: EventBridge Scheduler starts the state machines, replacing the CA7/Control-M definitions
  under `app/scheduler`. The daily chain is `DALYVRFY` → `POSTTRAN` → `TRANREPT`. `MNTTRDB2` runs on demand.
* **Infrastructure as code**: each job module ships `deploy/` JSON templates (job definition and ASL).
  Provisioning them (CDK/Terraform) is a follow-up. Nothing in this effort deploys to AWS.

## 3. Shared module layout

```
app/java/carddemo-batch/
├── pom.xml                       parent; all 7 job modules pre-registered (no pom edits needed per job)
├── mvnw, .mvn/                   Maven wrapper (3.9.9)
├── Dockerfile                    one image per job module
├── deploy/                       job-definition and state-machine templates
├── databricks/load_carddemo_output.py
├── batch-common/                 production library, used by every job
│   └── com.carddemo.batch.common
│       ├── record/   FieldSpec, RecordLayout, CobolRecord, Copybooks, ZonedDecimal, FixedWidthFile, KeyedFile
│       ├── schema/   ColumnType, Column, DatasetSchema (→ Spark StructType JSON and Avro/Parquet schema)
│       ├── output/   DatasetWriter, ParquetDatasetWriter, CsvDatasetWriter, OutputFormat, OutputLayout, Values
│       ├── io/       Storage (local paths and s3:// fetch/publish via AWS SDK v2)
│       └── job/      BatchJob, JobContext, JobResult, JobRunner, AbendException, BatchLauncher
├── batch-testkit/                test-only helpers: JobHarness, SampleData, DatasetFiles, ParityAssertions, SchemaContract
└── jobs/
    ├── cbact01c/  cbact02c/  cbact03c/  cbtrn01c/  cbtrn02c/  cbtrn03c/  cobtupdt/
    │   ├── pom.xml                       already present; shaded jar <module>-job.jar
    │   ├── src/main/java/com/carddemo/batch/<module>/   <Program>Job implements BatchJob (+ helpers)
    │   ├── src/main/resources/META-INF/services/com.carddemo.batch.common.job.BatchJob
    │   ├── src/main/resources/schemas/<dataset>.schema.json   checked-in schema contract per output
    │   ├── src/test/java/…               parity tests (+ unit tests)
    │   ├── src/test/resources/fixtures/<scenario>/{input,expected}/ and fixtures/README.md
    │   ├── tools/cobol/ (optional)       GnuCOBOL harness used to generate golden outputs
    │   └── deploy/                       <program> job definition + <JCLJOB>.asl.json
```

**What the common module provides**

| Mainframe construct | Java replacement |
|---|---|
| Copybook 01-level record | `RecordLayout` (`Copybooks.ACCOUNT_RECORD`, `CARD_RECORD`, `CARD_XREF_RECORD`, `CUSTOMER_RECORD`, `TRAN_CAT_BAL_RECORD`, `TRAN_TYPE_RECORD`, `TRAN_CAT_RECORD`, `TRAN_RECORD`, `DALYTRAN_RECORD`) and `CobolRecord` (`set` applies MOVE semantics: pad/truncate, rescale/truncate numerics) |
| Zoned decimal `PIC S9(n)V99` | `ZonedDecimal` (decodes EBCDIC-style and GnuCOBOL ASCII overpunch; encodes EBCDIC-style to match the samples) |
| QSAM/PS file, ESDS, ASCII unload of a KSDS | `FixedWidthFile` (LF/CRLF tolerant, short lines space-padded, blank lines skipped) |
| VSAM KSDS (random READ/WRITE/REWRITE/DELETE, sequential browse) | `KeyedFile` (in-memory `TreeMap` on the raw key image; statuses `OK`/`DUPLICATE_KEY`/`NOT_FOUND` ≙ file status 00/22/23). Data volumes are small (KB–MB); if volumes grow, swap in a disk-backed implementation behind the same API. |
| Db2 table | Snapshot file (initial state) loaded into `KeyedFile` or a `TreeMap`; post-run snapshot published as a Parquet dataset. SQLCODEs are modelled explicitly (0, +100, −803 duplicate, −532 FK restrict). |
| DD name | `JobContext.input("DALYTRAN")`, passed as `--input DALYTRAN=s3://…` |
| DISPLAY / SYSOUT | `JobContext.sysout()` (stdout → CloudWatch). When DISPLAY is the program's only business output (CBACT02C, CBACT03C, CBTRN01C), the job also publishes it as a dataset (§5). |
| `FUNCTION CURRENT-DATE` | `JobContext.clock()` (fixed in tests and via `--clock`) |
| `CALL 'CEE3ABD'` | `throw new AbendException(999, …)` → no outputs published, exit 99 |
| RETURN-CODE | `JobResult.returnCode()` |

**Rules for the parallel job sessions**

* Change only `jobs/<module>/**` and `docs/modernization/business-rules/<PROGRAM>.md`. Do **not** edit
  `batch-common`, `batch-testkit`, the parent `pom.xml`, or another module, so the seven PRs never conflict.
  If a helper looks reusable, keep it in your module and list it in the PR description as a candidate for common.
* Java package `com.carddemo.batch.<module>`. Main class `<Program>Job` (for example `Cbtrn02cJob`), registered via `META-INF/services`.
* Keep the COBOL paragraph names visible: one method per paragraph where practical (`validateTran()` with a
  `// 1500-VALIDATE-TRAN` reference), so reviewers can compare side by side.

## 4. Record layout → Java → Parquet / Databricks schema mapping

### 4.1 Type mapping (implemented by `DatasetSchema.fromLayout`)

| COBOL | Java (`CobolRecord`) | Parquet (via Avro) | Spark / Databricks | Notes |
|---|---|---|---|---|
| `PIC X(n)` | `String`, space-padded to n | `BINARY (STRING)` | `string` | Written **right-trimmed**. Leading spaces kept. Dates (`X(10)` `YYYY-MM-DD`) and timestamps (`X(26)`) stay strings with their exact COBOL text (formats differ between sources, for example `2022-06-10 19:27:53.000000` vs the Db2 `2022-07-06-00.00.00.000000`). Databricks views cast them. |
| `PIC 9(n)` (unsigned, no V): ids, codes, key parts | `String` of n digits, zero-padded | `BINARY (STRING)` | `string` | Keeps leading zeros and key collation (`ACCT-ID` `00000000001`). |
| `PIC S9(i)V9(s)` / `PIC 9(i)V9(s)` DISPLAY | `BigDecimal`, scale s | `BINARY (DECIMAL(i+s, s))` | `decimal(i+s,s)` | Exact; no floating point anywhere. |
| `COMP-3` (packed) in output records (CBACT01C) | `BigDecimal` | `DECIMAL` | `decimal` | Packed encoding is a storage detail. Parity compares values. |
| `OCCURS n` (CBACT01C ARRYFILE) | one column per occurrence, suffix `_1`…`_n` | flat columns | flat columns | Flat columns keep the schema simple to load. |
| `FILLER` | omitted | omitted | omitted | |
| Job-produced counters and line numbers | `long` / `int` | `INT64` / `INT32` | `long` / `integer` | |
| Flags derived by the job | `boolean` | `BOOLEAN` | `boolean` | |

Column names are the COBOL names lower-cased with `-` → `_` (`ACCT-EXPIRAION-DATE` → `acct_expiraion_date`;
the copybook typo is kept for traceability). Columns are non-nullable unless the dataset defines otherwise.
Each column's schema metadata records `cobol_field`, `copybook`, `pic`, 1-based `offset` and `length`.

### 4.2 Schema files

* Each output is published with `_schema.json` next to its data file. The schema is Spark `StructType` JSON
  (`DatasetSchema.toSparkJson()`), which loads with `StructType.fromJson(json.loads(...))`.
* The same JSON is checked in as `jobs/<module>/src/main/resources/schemas/<dataset>.schema.json`. A
  `SchemaContract.assertMatches(...)` test fails if code and contract drift; regenerate with
  `-Dcarddemo.updateSchemas=true`.
* Databricks always reads with the explicit schema (`spark.read.schema(schema)`, no inference), so loads are
  deterministic. CSV is read with `header=true`, `mode=FAILFAST` (`databricks/load_carddemo_output.py`).

### 4.3 Formats

* **Parquet** (datasets): one file per dataset per run, `part-00000.parquet`, SNAPPY. Rows are in COBOL
  processing order (input order or KSDS key order, whichever the program uses), so output is deterministic.
* **CSV** (reports): RFC 4180, UTF-8, header row, `,`, LF, quoted only when needed (including values with
  leading/trailing spaces). Decimals are plain (`-919.00`). Report datasets carry a `line_text` column holding
  the exact print-line image, so the legacy report can be reproduced byte-for-byte from the CSV.

## 5. Outputs per program (dataset contracts)

Dataset names are `snake_case`, unique within a program. The child sessions finalize columns in the
checked-in schema files. The datasets themselves are fixed here:

| Program | Dataset (format) | Content |
|---|---|---|
| CBACT01C | `account_out` (Parquet) | OUTFILE record per account (CBACT01C `OUT-ACCT-REC` fields; reissue date as produced by `COBDATFT` type 2→2). |
| | `account_array` (Parquet) | ARRYFILE record (`arr_acct_id`, `arr_acct_curr_bal_1..5`, `arr_acct_curr_cyc_debit_1..5`). |
| | `account_vbrc` (Parquet) | VBRCFILE records in write order: `record_type` (1/2), `record_length` (12/39), the populated fields, nullable for the type that lacks them. |
| CBACT02C | `card_listing` (Parquet) | Card records in KSDS key order (what the program DISPLAYs). |
| | `sysout` (CSV) | `line_no`, `line_text`: the exact DISPLAY lines. |
| CBACT03C | `xref_listing` (Parquet), `sysout` (CSV) | Same pattern for CARDXREF. |
| CBTRN01C | `daily_tran_verification` (Parquet) | One row per daily transaction processed: ids, card number, xref/account lookup outcome, resolved account/customer id. |
| | `sysout` (CSV) | DISPLAY lines. |
| CBTRN02C | `posted_transactions` (Parquet) | TRANFILE records written (CVTRA05Y). |
| | `daily_rejects` (Parquet) | DALYREJS: the rejected daily record (CVTRA06Y columns) + `validation_fail_reason` (`string`, 4 digits) + `validation_fail_reason_desc`. |
| | `account_master` (Parquet) | Full ACCTFILE after posting, key order. |
| | `tran_cat_balance` (Parquet) | Full TCATBALF after posting, key order. |
| CBTRN03C | `transaction_report` (CSV) | One row per report line: `line_no`, `page_no`, `line_type` (`HEADER`, `BLANK`, `COLUMN_HEADER`, `RULE`, `DETAIL`, `PAGE_TOTAL`, `ACCOUNT_TOTAL`, `GRAND_TOTAL`), detail fields (nullable), `amount`, and `line_text` (the exact 133-byte line, right-trimmed). |
| COBTUPDT | `transaction_type` (Parquet) | Post-maintenance snapshot of `CARDDEMO.TRANSACTION_TYPE` ordered by `tr_type` (the `TRANEXTR` extract order): `tr_type`, `tr_description`. |
| | `maintenance_log` (CSV) | One row per input record: `record_no`, `input_type`, `tr_type`, `tr_description`, `action`, `sqlcode`, `result`, `message`. |

`_manifest.json` (per run) lists every dataset with its row count, the counters and the return code.

## 6. S3 / Databricks naming and partitioning

```
s3://<bucket>/carddemo/
├── raw/{dd-or-file-name}/run_date={yyyy-MM-dd}/{file}         inputs, mainframe ASCII unload format
└── curated/{program}/{dataset}/run_date={yyyy-MM-dd}/run_id={run_id}/part-00000.{parquet|csv}
                                                              /_schema.json
    curated/{program}/_runs/run_date={yyyy-MM-dd}/run_id={run_id}/_manifest.json
```

* `program` is lower case (`cbtrn02c`). `run_date` is the business date (`--run-date`). `run_id` is the Step
  Functions execution name, or `AWS_BATCH_JOB_ID` for ad-hoc runs. Paths are produced only by `OutputLayout`.
* Outputs are immutable. A rerun writes a new `run_id` and never overwrites. Databricks loads one
  `(run_date, run_id)` with `replaceWhere`, so loads are idempotent.
* `run_date`/`run_id` are Hive-style partitions (Spark discovers them). `_`-prefixed files are ignored by Spark readers.
* Databricks table names: `carddemo.{program}_{dataset}` (for example `carddemo.cbtrn02c_posted_transactions`),
  Delta, partitioned by `run_date`.
* Chaining jobs: a downstream job takes the upstream run's files as inputs. A master-file update
  (`account_master`, `tran_cat_balance`) is the next run's input after a `KeyedFile`-compatible unload. Jobs
  accept their legacy fixed-width inputs, so the S3 `raw/` zone can be fed directly from mainframe unloads during
  the parallel-run period.

## 7. Parity testing

**Goal**: for the same inputs, the Java job's outputs equal what the COBOL program produces, value for value
and row for row, including the program's quirks.

### 7.1 Mechanics (all JUnit 5, `./mvnw verify`)

* `JobHarness.forJob(job, tempDir).input(DD, path)…run()` runs the job in-process with run date 2022-07-06,
  run id `parity`, and clock fixed at `2022-07-06T00:00:00Z`. Tests use `SampleData.file("acctdata.txt")` to read
  `app/data/ASCII` directly. The parent POM passes the path as `-Dcarddemo.sampleData`.
* `ParityAssertions.assertParquetMatches(actual.parquet, expected.csv)`, `assertCsvMatches`, and
  `assertTextMatches` compare rows in order, column by column, with canonical values (`Values.canonical`:
  strings as written, decimals plain with full scale, nulls empty).
* `SchemaContract.assertMatches(schema, moduleDir)` pins each published schema.
* Every program also asserts its `JobResult` (return code, counters) and its key SYSOUT lines.

### 7.2 Fixtures

```
jobs/<module>/src/test/resources/fixtures/
├── README.md                       how every expected file was derived (required)
├── sample-data/expected/<dataset>.csv|.txt     expected outputs for app/data/ASCII inputs
└── <edge-case-scenario>/{input/…, expected/…}  small hand-built inputs per rule/branch
```

* **Expected outputs come from the COBOL program's rules**, never from running the Java job. Preferred
  derivation: compile the original program under GnuCOBOL 3.x (`apt-get install gnucobol3`, which ships with BDB
  indexed files) with the smallest possible shims (for example a stub for `CEE3ABD`, a COBOL port of `COBDATFT`,
  a loader that builds the KSDS from the ASCII file, and `-fsign=EBCDIC` so signs match the samples). Run it on the
  fixture inputs and convert its output to the canonical CSV with a small checked-in script. Keep the harness under
  `jobs/<module>/tools/cobol/` with a README. When the original cannot be run (`COBTUPDT` uses `EXEC SQL`), derive
  the expected output by hand or with an independent script that follows the documented rules. Never derive it
  with the Java job.
* Edge cases: at least one scenario per business rule or branch that the sample data does not exercise
  (for example every CBTRN02C reject reason, tcatbal create vs. update, CBTRN03C page breaks and the
  last-account total, COBTUPDT add/update/delete/not-found/duplicate/invalid type/comment/FK-restricted delete).
* Fixture inputs that do not exist in the sample data are derived deterministically from it, and the derivation
  is documented in `fixtures/README.md`:
  * **CBTRN03C TRANFILE**: `dailytran.txt` records (CVTRA06Y and CVTRA05Y have identical layouts) with
    `TRAN-PROC-TS` set from `TRAN-ORIG-TS`, in input order. The job's REPRO/SORT/INCLUDE pre-step then sorts them
    by card number and filters by `PARM-START-DATE`/`PARM-END-DATE` (`TRANREPT` defaults 2022-01-01..2022-07-06,
    read from DATEPARM).
  * **COBTUPDT INPFILE**: a checked-in maintenance file covering every branch. The initial table is
    `trantype.txt` (`TRAN-TYPE` → `TR_TYPE`, right-trimmed `TRAN-TYPE-DESC` → `TR_DESCRIPTION`). The FK side is
    `trancatg.txt`.

### 7.3 CI

`.github/workflows/carddemo-batch.yml` runs `./mvnw -B verify` on every PR that touches
`app/java/carddemo-batch/**`. If Actions is unavailable on the fork, each PR description records the local
`./mvnw -B verify` result (test counts per module).

> Local note: if Maven Central answers HTTP 429 from your network, add a `central` mirror to `~/.m2/settings.xml`
> pointing at `https://maven-central.storage-download.googleapis.com/maven2/`.

## 8. Per-program migration order

The shared contracts (layouts, schema rules, naming, test kit) land with this design, so all seven programs can
be built in parallel. The order below is for **review, merge and cutover**. Each step proves something the next
one builds on:

| Order | Program | Why here |
|---|---|---|
| 1 | CBACT02C | Simplest: sequential KSDS browse plus listing. Proves the end-to-end path (layout → Parquet → schema → Databricks). |
| 2 | CBACT03C | Same pattern on the xref, which every transaction program relies on. |
| 3 | CBACT01C | First reformatting logic: date conversion (`COBDATFT`), COMP-3/OCCURS/variable-length outputs, and the decimal mapping. |
| 4 | COBTUPDT | Db2 track: proves the table-snapshot pattern and SQLCODE modelling. Produces the `transaction_type` reference data that the report uses. Independent of the VSAM chain. |
| 5 | CBTRN01C | Read-only validation over five keyed files. Proves random-access `KeyedFile` lookups without updates. |
| 6 | CBTRN02C | Core posting: updates master files, writes rejects, RC 4. Highest business risk, so it goes after the patterns above are proven. |
| 7 | CBTRN03C | Consumes `CBTRN02C`'s transactions. Report pagination and totals, plus the folded-in SORT step. |

Cutover per program: run COBOL and Java side by side on production unloads for N cycles. Diff the outputs with
the same `ParityAssertions` (as a Databricks notebook or a CI job). Switch the scheduler entry to the Step
Functions state machine once N consecutive cycles have zero diffs.

## 9. Risks and decisions log

| Topic | Decision |
|---|---|
| Behavioral quirks (for example CBTRN03C `NEXT SENTENCE` in the date filter, CBTRN01C re-processing the last record at EOF, CBACT01C leaving `OUT-ACCT-CURR-CYC-DEBIT` unset when the input is non-zero, COBTUPDT continuing after "abend") | Reproduce by default for parity. Document each in the program's business-rules doc under "Quirks preserved". A fix is a separate, explicit change. |
| `PIC X` trailing spaces | Right-trimmed in Parquet/CSV; COBOL comparisons are space-padded, so semantics are unchanged. Exact images stay available via `line_text` and `toFixedWidth()`. |
| Timestamps | Kept as COBOL text. `CURRENT-DATE` comes from the injected clock. |
| In-memory KSDS | Fine for these volumes; the API allows a disk-backed replacement. |
| Hadoop on the classpath | `parquet-avro` needs a few `hadoop-common` classes. All Hadoop transitive dependencies are excluded and the few needed ones are pinned in `batch-common/pom.xml`. Files are written through `LocalOutputFile` and uploaded with the AWS SDK, never via HDFS/S3A. |
