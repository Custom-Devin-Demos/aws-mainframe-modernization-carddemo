# CBTRN01C: daily transaction verification

Source: `app/cbl/CBTRN01C.cbl`. Java: `app/java/carddemo-batch/jobs/cbtrn01c`
(`com.carddemo.batch.cbtrn01c.Cbtrn01cJob`). Parity evidence: GnuCOBOL 3.1.2 runs of the original program
(`jobs/cbtrn01c/tools/cobol/`, `src/test/resources/fixtures/README.md`).

## 1. Purpose and JCL context

CBTRN01C reads the daily transaction file and checks that each transaction's card number exists in the card
cross-reference and that the cross-referenced account exists in the account master. It only DISPLAYs the
outcome: it writes no file, updates nothing, and never sets RETURN-CODE.

There is no JCL for CBTRN01C in `app/jcl`. The DD names come from the `SELECT ... ASSIGN TO` clauses:
`DALYTRAN`, `CUSTFILE`, `XREFFILE`, `CARDFILE`, `ACCTFILE`, `TRANFILE`. On AWS it is the single step of the
new Step Functions state machine `DALYVRFY` (`jobs/cbtrn01c/deploy/DALYVRFY.asl.json`), the first job of the
daily chain `DALYVRFY` → `POSTTRAN` → `TRANREPT` (design §2). Upstream: the daily transaction unload and the
account/card master unloads in S3 `raw/`. Downstream: none consume its outputs; they are an audit trail.

## 2. Inputs and outputs

| COBOL file (DD) | Copybook / FD | Organization, access | Used? | Java input | 
|---|---|---|---|---|
| `DALYTRAN-FILE` (`DALYTRAN`) | FD `FD-TRAN-RECORD` (X(16)+X(334)) read `INTO` `DALYTRAN-RECORD` (CVTRA06Y, 350 bytes) | PS, sequential | read | `ctx.input("DALYTRAN")`, `FixedWidthFile` |
| `CUSTOMER-FILE` (`CUSTFILE`) | CVCUS01Y, 500 bytes, key `FD-CUST-ID` | KSDS, random | **opened and closed only, never read** | `ctx.optionalInput("CUSTFILE")` (must exist) |
| `XREF-FILE` (`XREFFILE`) | CVACT03Y, 50 bytes, key `FD-XREF-CARD-NUM` | KSDS, random | read by key | `KeyedFile` on `XREF-CARD-NUM` |
| `CARD-FILE` (`CARDFILE`) | CVACT02Y, 150 bytes, key `FD-CARD-NUM` | KSDS, random | **opened and closed only, never read** | `ctx.optionalInput("CARDFILE")` (must exist) |
| `ACCOUNT-FILE` (`ACCTFILE`) | CVACT01Y, 300 bytes, key `FD-ACCT-ID` | KSDS, random | read by key | `KeyedFile` on `ACCT-ID` |
| `TRANSACT-FILE` (`TRANFILE`) | CVTRA05Y, 350 bytes, key `FD-TRANS-ID` | KSDS, random | **opened and closed only, never read** | `ctx.optionalInput("TRANFILE")` (must exist) |

Outputs (design §5), written through `JobContext.openDataset(...)`:

| Dataset | Format | Content |
|---|---|---|
| `daily_tran_verification` | Parquet | One row per XREF lookup, i.e. per DALYTRAN record **plus the EOF re-lookup** (BR-CBTRN01C-07). Columns: `lookup_seq`, `dalytran_record_no`, `eof_reprocessed`, `dalytran_id`, `dalytran_card_num`, `xref_read_status`, `xref_acct_id`, `xref_cust_id`, `acct_read_status`, `verification_status`. Schema: `src/main/resources/schemas/daily_tran_verification.schema.json`. |
| `sysout` | CSV | Every DISPLAY line: `line_no`, `line_text` (right-trimmed). The same lines, untrimmed, go to the job's SYSOUT stream (CloudWatch). Schema: `src/main/resources/schemas/sysout.schema.json`. |

## 3. Processing rules

- **BR-CBTRN01C-01** (`MAIN-PARA`): DISPLAY `START OF EXECUTION OF PROGRAM CBTRN01C`, then open the six files in
  order `0000-DALYTRAN-OPEN`, `0100-CUSTFILE-OPEN`, `0200-XREFFILE-OPEN`, `0300-CARDFILE-OPEN`,
  `0400-ACCTFILE-OPEN`, `0500-TRANFILE-OPEN`. Each is `OPEN INPUT`; any status other than `00` abends (§4).
- **BR-CBTRN01C-02** (`1000-DALYTRAN-GET-NEXT`): `READ DALYTRAN-FILE INTO DALYTRAN-RECORD`. Status `00`:
  `MAIN-PARA` DISPLAYs the whole 350-byte `DALYTRAN-RECORD`. Status `10`: `END-OF-DAILY-TRANS-FILE` = `'Y'`.
  Any other status abends (§4).
- **BR-CBTRN01C-03** (`MAIN-PARA`, `2000-LOOKUP-XREF`): `MOVE 0 TO WS-XREF-READ-STATUS`, key =
  `DALYTRAN-CARD-NUM`, random `READ XREF-FILE INTO CARD-XREF-RECORD`. Found: DISPLAY
  `SUCCESSFUL READ OF XREF`, `CARD NUMBER: <XREF-CARD-NUM>`, `ACCOUNT ID : <XREF-ACCT-ID>`,
  `CUSTOMER ID: <XREF-CUST-ID>`. INVALID KEY: DISPLAY `INVALID CARD NUMBER FOR XREF`, `WS-XREF-READ-STATUS` = 4.
- **BR-CBTRN01C-04** (`MAIN-PARA`): when `WS-XREF-READ-STATUS` ≠ 0, DISPLAY
  `CARD NUMBER <DALYTRAN-CARD-NUM> COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-<DALYTRAN-ID>`; the account
  is not read. Row: `xref_read_status` 4, `xref_acct_id`/`xref_cust_id`/`acct_read_status` null,
  `verification_status` = `CARD_NOT_VERIFIED`.
- **BR-CBTRN01C-05** (`MAIN-PARA`, `3000-READ-ACCOUNT`): when the XREF was found, `MOVE 0 TO
  WS-ACCT-READ-STATUS`, key = `XREF-ACCT-ID`, random `READ ACCOUNT-FILE INTO ACCOUNT-RECORD`. Found: DISPLAY
  `SUCCESSFUL READ OF ACCOUNT FILE`, `verification_status` = `VERIFIED`. INVALID KEY: DISPLAY
  `INVALID ACCOUNT NUMBER FOUND`, `WS-ACCT-READ-STATUS` = 4.
- **BR-CBTRN01C-06** (`MAIN-PARA`): when `WS-ACCT-READ-STATUS` ≠ 0, DISPLAY `ACCOUNT <ACCT-ID> NOT FOUND`;
  `verification_status` = `ACCOUNT_NOT_FOUND`, `acct_read_status` 4. Nothing is skipped or written; the
  program just goes on to the next record.
- **BR-CBTRN01C-07** (`MAIN-PARA`): the XREF/account lookup of BR-03..06 runs on every pass of the loop
  including the pass whose READ hit EOF, using whatever `DALYTRAN-RECORD` still holds (see §5).
- **BR-CBTRN01C-08** (`MAIN-PARA`, `9000-DALYTRAN-CLOSE` .. `9500-TRANFILE-CLOSE`): close the six files,
  DISPLAY `END OF EXECUTION OF PROGRAM CBTRN01C`, `GOBACK`. RETURN-CODE is never set, so a normal end is RC 0
  (`JobResult.ok`).
- **BR-CBTRN01C-09** (`0100-CUSTFILE-OPEN`, `0300-CARDFILE-OPEN`, `0500-TRANFILE-OPEN`): CUSTFILE, CARDFILE
  and TRANFILE are opened and closed but never read. They are still required inputs, because an OPEN failure
  abends; their content is ignored (an empty file is fine).
- **BR-CBTRN01C-10** (Java only): counters `records_read` (successful DALYTRAN READs), `lookups`, `verified`,
  `card_not_verified`, `account_not_found`, `eof_reprocessed` are returned in `JobResult` and the manifest.
  The COBOL keeps no counters; each one equals a count of SYSOUT lines.

## 4. Error handling

| Point | COBOL | Java |
|---|---|---|
| OPEN of any DD, status ≠ `00` | `ERROR OPENING DAILY TRANSACTION FILE` / `CUSTOMER FILE` / `CROSS REF FILE` / `CARD FILE` / `ACCOUNT FILE` / `TRANSACTION FILE`, then `Z-DISPLAY-IO-STATUS`, `Z-ABEND-PROGRAM` | DD not supplied or file missing = status `35`: same three lines, then `AbendException(999)` |
| DALYTRAN READ, status not `00`/`10` | `ERROR READING DAILY TRANSACTION FILE`, `Z-DISPLAY-IO-STATUS`, `Z-ABEND-PROGRAM` | a record `FixedWidthFile` rejects (longer than 350 bytes) = status `04` (record-length condition), raised after the good records before it were processed |
| XREF / ACCOUNT READ | `INVALID KEY` only (BR-03, BR-05); no status check | `KeyedFile.read` empty = INVALID KEY |
| CLOSE, status ≠ `00` | `ERROR CLOSING ... FILE`, `Z-DISPLAY-IO-STATUS`, `Z-ABEND-PROGRAM` | not reproducible: inputs are read into memory and closing cannot fail |
| `Z-DISPLAY-IO-STATUS` | `FILE STATUS IS: NNNN` + `IO-STATUS-04`: `00` + status when numeric and not `9x`; otherwise stat1 + 3-digit binary value of stat2 | `Cbtrn01cJob.ioStatus04` |
| `Z-ABEND-PROGRAM` | DISPLAY `ABENDING PROGRAM`, `CALL 'CEE3ABD'` ABCODE 999, TIMING 0 | DISPLAY `ABENDING PROGRAM`, throw `AbendException(999)`; `JobRunner` publishes no dataset and no manifest |

## 5. Quirks preserved for parity

- **EOF re-lookup of the last record (BR-CBTRN01C-07).** In `MAIN-PARA` only `DISPLAY DALYTRAN-RECORD` is
  inside the inner `IF END-OF-DAILY-TRANS-FILE = 'N'`; the `MOVE DALYTRAN-CARD-NUM TO XREF-CARD-NUM`,
  `2000-LOOKUP-XREF` and `3000-READ-ACCOUNT` are not. A failed `READ ... INTO` does not move anything, so on the
  EOF pass the last record is looked up a second time without being DISPLAYed. GnuCOBOL evidence: the
  300-record sample produces 301 XREF/account lookup blocks in SYSOUT, the last two both for card
  `3260763612337560`. Java emits the extra lookup in both outputs: its SYSOUT lines, and a
  `daily_tran_verification` row with `eof_reprocessed = true` and the last record's `dalytran_record_no`.
  If the last card is unknown (or its account missing), the "COULD NOT BE VERIFIED" (or "NOT FOUND") message
  appears twice.
- **Empty DALYTRAN still does one lookup.** With no records the EOF pass uses the initial
  `DALYTRAN-RECORD`, which has no VALUE clause. GnuCOBOL initialises it to spaces (alphanumeric) and zeros
  (numeric), so the card number is 16 spaces: `INVALID CARD NUMBER FOR XREF` and
  `CARD NUMBER <16 spaces> COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-<16 spaces>`. Java reproduces the
  GnuCOBOL initial value (`RecordLayout.newRecord()`), giving one row with `dalytran_record_no` null. On z/OS the
  initial content depends on compiler/runtime storage options; the GnuCOBOL behaviour is the documented reference.
- **Untrimmed DISPLAY of the record.** `DISPLAY DALYTRAN-RECORD` prints all 350 bytes including trailing
  spaces; the SYSOUT stream keeps them, the `sysout` CSV right-trims (design §4).
- **Opened-but-unread files** (BR-CBTRN01C-09) stay required inputs.
- **Not reproduced:** CLOSE failures (cannot happen for in-memory inputs); DALYTRAN statuses other than
  `00`/`10`/`04`/`35` (no Java equivalent); the timing of SYSOUT relative to other jobs.

## 6. Traceability

| Rule | Java (`Cbtrn01cJob.Execution` unless noted) | Test (`Cbtrn01cParityTest` unless noted) |
|---|---|---|
| BR-CBTRN01C-01 | `mainPara`, `dalytranOpen`, `custfileOpen`, `xreffileOpen`, `cardfileOpen`, `acctfileOpen`, `tranfileOpen` | `matchesCobolOutputs[*]`, `openFailureAbends[*]` |
| BR-CBTRN01C-02 | `dalytranGetNext`, `mainPara` | `matchesCobolOutputs[sample-data]`, `sampleDataVerifiesEveryTransactionAndRepeatsTheLastLookupAtEof` |
| BR-CBTRN01C-03 | `lookupXref` | `matchesCobolOutputs[xref-not-found, mixed-outcomes]`, `keySysoutLinesForFailedLookups` |
| BR-CBTRN01C-04 | `mainPara`, `writeVerification` | `matchesCobolOutputs[xref-not-found]`, `keySysoutLinesForFailedLookups` |
| BR-CBTRN01C-05 | `readAccount` | `matchesCobolOutputs[sample-data, account-not-found]` |
| BR-CBTRN01C-06 | `mainPara`, `writeVerification` | `matchesCobolOutputs[account-not-found, mixed-outcomes]`, `keySysoutLinesForFailedLookups` |
| BR-CBTRN01C-07 | `mainPara` (lookup outside the inner IF), `writeVerification` | `matchesCobolOutputs[*]`, `lastRecordNotInXrefIsReportedTwice`, `emptyDalytranLooksUpTheInitialRecordOnce` |
| BR-CBTRN01C-08 | `closeFiles`, `mainPara` | `matchesCobolOutputs[*]` (RC 0, END line) |
| BR-CBTRN01C-09 | `custfileOpen`, `cardfileOpen`, `tranfileOpen`, `openInput` | `openFailureAbends[CUSTFILE, CARDFILE, TRANFILE]` |
| BR-CBTRN01C-10 | `mainPara`, `writeVerification` | `matchesCobolOutputs[*]` (counters vs `result.properties`) |
| §4 OPEN | `openInput`, `zDisplayIoStatus`, `zAbendProgram` | `openFailureAbends[*]`, `missingFileForSuppliedDdIsAlsoStatus35` |
| §4 READ | `dalytranGetNext`, `dalytranOpen` | `readErrorAbendsAfterTheGoodRecords` |
| §4 IO status | `Cbtrn01cJob.ioStatus04` | `Cbtrn01cJobTest.ioStatus04[*]` |
| Schemas | `Cbtrn01cDatasets`, `SysoutDataset` | `Cbtrn01cJobTest.dailyTranVerificationSchemaContract`, `sysoutSchemaContract` |
