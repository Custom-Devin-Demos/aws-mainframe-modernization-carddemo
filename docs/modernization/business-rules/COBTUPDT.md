# COBTUPDT — Db2 transaction-type maintenance

Source: `app/app-transaction-type-db2/cbl/COBTUPDT.cbl`. Java: `app/java/carddemo-batch/jobs/cobtupdt`
(`com.carddemo.batch.cobtupdt.CobtupdtJob`). Parity tests: `CobtupdtParityTest`.

## 1. Purpose and JCL context

* Job `MNTTRDB2` (`app/app-transaction-type-db2/jcl/MNTTRDB2.jcl`), step `STEP1`: `IKJEFT01` running
  `DSN SYSTEM(DAZ1) RUN PROGRAM(COBTUPDT) PLAN(CARDDEMO)`. Runs on demand, outside the daily chain.
* DD names: `INPFILE` (maintenance file). `SYSTSPRT` receives the DISPLAY output.
* Applies add/update/delete maintenance to `CARDDEMO.TRANSACTION_TYPE`. Downstream, `TRANEXTR` unloads the table
  (ordered by `TR_TYPE`) into the `TRANTYPE` file used by the online/batch programs.
* AWS: one AWS Batch job (`deploy/batch-job-definition.json`), Step Functions `deploy/MNTTRDB2.asl.json` with
  a single `STEP1` state.

## 2. Inputs and outputs

| Legacy | Organization / access | Layout | Java |
|---|---|---|---|
| `INPFILE` | PS, FB 53, sequential input | FD `TR-RECORD` / `WS-INPUT-REC`: `INPUT-REC-TYPE X(1)`, `INPUT-REC-NUMBER X(2)`, `INPUT-REC-DESC X(50)` | DD `INPFILE`, `CobtupdtJob.INPUT_RECORD` |
| `CARDDEMO.TRANSACTION_TYPE` | Db2 table, INSERT / UPDATE / DELETE by key | `ddl/TRNTYPE.ddl`: `TR_TYPE CHAR(2)` PK, `TR_DESCRIPTION VARCHAR(50)` | initial state DD `TRANTYPE` (CVTRA03Y unload, `app/data/ASCII/trantype.txt`) loaded into a `KeyedFile`; final state dataset `transaction_type` (Parquet) |
| `CARDDEMO.TRANSACTION_TYPE_CATEGORY` | Db2 table, read only (FK `TRC_TYPE_CODE` → `TR_TYPE`, ON DELETE RESTRICT) | `ddl/TRNTYCAT.ddl` | DD `TRANCATG` (CVTRA04Y unload, `app/data/ASCII/trancatg.txt`) |
| `SYSTSPRT` DISPLAY lines | SYSOUT | text | `ctx.sysout()`; plus dataset `maintenance_log` (CSV) |

`transaction_type`: `tr_type`, `tr_description` (right-trimmed), ordered by `tr_type` in the EBCDIC collation.
`maintenance_log`: `record_no`, `input_type`, `tr_type`, `tr_description` (input fields, right-trimmed),
`action` (`INSERT`/`UPDATE`/`DELETE`/`COMMENT`/`INVALID`), `sqlcode` (null when no SQL ran),
`result` (`OK`/`NOT_FOUND`/`SQL_ERROR`/`IGNORED`/`INVALID_TYPE`), `message` (the last line DISPLAYed for the record).
Schemas: `src/main/resources/schemas/*.schema.json`.

Counters (`JobResult`): `records_read`, `comments_ignored`, `records_inserted`, `records_updated`,
`records_deleted`, `not_found`, `sql_errors`, `invalid_type`, `errors` (= the last three), `table_rows`.

## 3. Processing rules

* **BR-COBTUPDT-01** (`0001-OPEN-FILES`): OPEN INPUT INPFILE. Status `00` → DISPLAY `OPEN FILE OK`, otherwise
  `OPEN FILE NOT OK` (see §4). The paragraph has no PERFORM and no STOP RUN, so control **falls through** into
  `1001-READ-NEXT-RECORDS`: that fall-through is the program's main line.
* **BR-COBTUPDT-02** (`1001-READ-NEXT-RECORDS`, `1002-READ-RECORDS`): priming READ, then for each record
  `1003-TREAT-RECORD` and the next READ, until AT END. Each record read is DISPLAYed as `PROCESSING   ` followed
  by the 53-byte record. An empty line in the ASCII unload is an all-spaces record; text after column 53 must
  be blank. `records_read` counts records.
* **BR-COBTUPDT-03** (`1003-TREAT-RECORD`): `EVALUATE INPUT-REC-TYPE`, case sensitive: `A` → DISPLAY
  `ADDING RECORD` + insert; `U` → `UPDATING RECORD` + update; `D` → `DELETING RECORD` + delete; `*` →
  `IGNORING COMMENTED LINE` (no SQL, `comments_ignored`).
* **BR-COBTUPDT-04** (`1003-TREAT-RECORD` WHEN OTHER): any other type (blank, lower case, digit, ...) →
  STRING `ERROR: TYPE NOT VALID` INTO WS-RETURN-MSG and PERFORM `9999-ABEND` (`invalid_type`).
* **BR-COBTUPDT-05** (`10031-INSERT-DB`): `INSERT (TR_TYPE, TR_DESCRIPTION) VALUES (:INPUT-REC-NUMBER,
  :INPUT-REC-DESC)`. SQLCODE 0 → `RECORD INSERTED SUCCESSFULLY`. Key already present → SQLCODE `-803`.
* **BR-COBTUPDT-06** (`10032-UPDATE-DB`): `UPDATE ... SET TR_DESCRIPTION = :INPUT-REC-DESC WHERE TR_TYPE =
  :INPUT-REC-NUMBER`. 0 → `RECORD UPDATED SUCCESSFULLY`; no row → `+100` → STRING `No records found.` and
  `9999-ABEND`. A blank description sets the description to blanks.
* **BR-COBTUPDT-07** (`10033-DELETE-DB`): `DELETE ... WHERE TR_TYPE = :INPUT-REC-NUMBER`. 0 →
  `RECORD DELETED SUCCESSFULLY`; no row → `+100` → `No records found.` + `9999-ABEND`; a
  TRANSACTION_TYPE_CATEGORY row still references the type → `-532` (ON DELETE RESTRICT), row kept.
* **BR-COBTUPDT-08** (`10031/10032/10033`, WHEN SQLCODE < 0): MOVE SQLCODE TO `WS-VAR-SQLCODE PIC ----9`, STRING
  `Error accessing: TRANSACTION_TYPE table. SQLCODE:` + the 5-character edited value (e.g. ` -803`) INTO
  WS-RETURN-MSG, PERFORM `9999-ABEND`.
* **BR-COBTUPDT-09** (`9999-ABEND`): DISPLAY WS-RETURN-MSG, MOVE 4 TO RETURN-CODE, EXIT. It returns to the
  caller: processing continues with the next record. RC is 0 if no record failed, otherwise 4; a later success
  does not reset it.
* **BR-COBTUPDT-10** (`2001-CLOSE-STOP`, then `STOP RUN` in `1001`): close INPFILE and end. The resulting table is
  published as `transaction_type`, ordered like `TRANEXTR`'s `ORDER BY TR_TYPE` on Db2 for z/OS (EBCDIC CCSID 37:
  space < lower case < upper case < digits).
* **BR-COBTUPDT-11** (snapshot model, DDL): the table is loaded from `TRANTYPE` (`TRAN-TYPE` → `TR_TYPE`,
  `TRAN-TYPE-DESC` → `TR_DESCRIPTION`); duplicate keys in the unload are rejected (PK). The FK side is loaded from
  `TRANCATG` and is read only. Host variables are fixed `PIC X(2)` / `PIC X(50)`, so Db2 stores the 50-byte
  description including trailing blanks in the VARCHAR(50) column (no trimming happens on INSERT/UPDATE). The
  key compares as CHAR(2), always 2 bytes. Outputs right-trim both columns (design canonical form), so
  `Purchase` and `Purchase` + 42 blanks publish the same value.

## 4. Error handling

* No `CEE3ABD` and no file-status check after READ. All SQL errors and invalid types go through `9999-ABEND`,
  which is only a message + RC 4 (BR-COBTUPDT-09). The job therefore returns `JobResult` RC 0 or 4 and never
  abends for data errors. The launcher maps RC ≤ 4 to exit 0; the Step Functions state succeeds.
* SQLCODE model: `0` success, `+100` row not found (UPDATE/DELETE), `-803` duplicate key (INSERT), `-532`
  delete restricted by the FK. These are the only SQLCODEs a file snapshot can produce; any other negative code
  would take the same `SQLCODE < 0` branch. An INSERT can never return `+100`, and a positive code other than
  `+100` falls through the EVALUATE without a message (unreachable).
* OPEN failure (`OPEN FILE NOT OK`): the COBOL keeps going, READs an unopened file (status `47`, AT END never
  raised) and loops on the stale record area until the step is cancelled (`TIME=1440`). **Not reproduced**: the
  Java job DISPLAYs `OPEN FILE NOT OK` and throws `AbendException(999)` (exit 99, no outputs published).
* A missing DD (`INPFILE`, `TRANTYPE`, `TRANCATG`) is a JCL error on the mainframe; in Java `ctx.input` fails
  before the program starts. A record longer than 53 bytes with non-blank overflow is rejected (not possible in an
  FB 53 dataset).

## 5. Quirks preserved for parity

* **Continue after "abend"**: `9999-ABEND` is named like an abend but just DISPLAYs and sets RC 4 (COBOL:
  `DISPLAY WS-RETURN-MSG. MOVE 4 TO RETURN-CODE EXIT.`). Reproduced; fixture `message-carry-over`.
* **WS-RETURN-MSG is never cleared**: every `STRING ... DELIMITED BY SIZE INTO WS-RETURN-MSG` (no POINTER)
  overwrites only the leading positions, so a shorter message keeps the tail of a longer earlier one, e.g.
  `ERROR: TYPE NOT VALIDSACTION_TYPE table. SQLCODE: -532` and `No records found.TRANSACTION_TYPE table.
  SQLCODE: -803`. Reproduced in SYSOUT and in `maintenance_log.message`; fixtures `sample-data`,
  `message-carry-over`.
* **Fall-through main line**: `0001-OPEN-FILES` drops into `1001-READ-NEXT-RECORDS`; there is no driver
  paragraph. Reproduced by calling them in sequence from `execute()`.
* **Comment records are split like data records**: a `*` line still has its columns 2-3 and 4-53 logged as
  `tr_type` / `tr_description` (e.g. `* ONLY ...` logs `tr_type` ` O`).
* **Type is case sensitive and not validated as numeric**: the JCL says columns 2-3 are numeric, but the program
  does not check; any 2 characters are inserted as is (fixture `ebcdic-collation`).
* **Messages are the edited SQLCODE**: `SQLCODE: -803` keeps the leading blank of `PIC ----9`.
* Not reproduced: the OPEN-failure endless loop (§4).

## 6. Traceability

| Rule | Java (`CobtupdtJob.Run`) | Test (`CobtupdtParityTest`) |
|---|---|---|
| BR-COBTUPDT-01 | `openFiles`, `execute` | all scenario tests (`OPEN FILE OK`), `unreadableInpfileAbendsWithoutOutputs` |
| BR-COBTUPDT-02 | `readNextRecords`, `readRecords` | `edgeCaseScenarioMatchesCobolDerivedFixtures[invalid-and-blank-records]`, `[empty-input]`, `emptyInpfileOnlyOpensAndPublishesUnchangedSnapshot` |
| BR-COBTUPDT-03 | `treatRecord` | `sampleDataScenarioMatchesCobolDerivedFixtures`, `[all-success-rc0]` |
| BR-COBTUPDT-04 | `treatRecord` (default) | `sampleDataScenario...`, `[invalid-and-blank-records]`, `[message-carry-over]` |
| BR-COBTUPDT-05 | `insertDb`, `sqlInsert` | `sampleDataScenario...` (-803), `[all-success-rc0]`, `[ebcdic-collation]` |
| BR-COBTUPDT-06 | `updateDb`, `sqlUpdate` | `sampleDataScenario...` (+100), `[all-success-rc0]`, `[invalid-and-blank-records]` |
| BR-COBTUPDT-07 | `deleteDb`, `sqlDelete` | `sampleDataScenario...` (-532, +100), `[all-success-rc0]`, `[message-carry-over]` |
| BR-COBTUPDT-08 | `sqlErrorMessage`, `CobtupdtJob.editSqlcode` | `sampleDataScenario...`, `sqlcodeIsEditedAsPicMinusMinusMinusMinusNine` |
| BR-COBTUPDT-09 | `abend`, `stringIntoReturnMsg` | `processingContinuesAfter9999AbendAndRcStaysFour`, `successfulRunKeepsReturnCodeZero`, `[message-carry-over]` |
| BR-COBTUPDT-10 | `closeStop`, `writeSnapshot` | `[ebcdic-collation]`, all scenario tests (`transaction_type` Parquet) |
| BR-COBTUPDT-11 | `CobtupdtJob.loadTransactionType`, `Run(JobContext)` | `sampleDataScenario...`, `schemaContractsAreCheckedIn` |
