# CBTRN02C – Post daily transactions

Java: `app/java/carddemo-batch/jobs/cbtrn02c`, class `com.carddemo.batch.cbtrn02c.Cbtrn02cJob` (paragraph methods on
the inner `Run` class). COBOL: `app/cbl/CBTRN02C.cbl`.

## 1. Purpose and JCL context

Reads the day's card transactions, validates each against the card cross-reference and the account master, posts
valid ones (new transaction record, transaction-category balance, account balances) and writes invalid ones to a
reject file with a reason trailer.

* JCL: `app/jcl/POSTTRAN.jcl`, job `POSTTRAN`, step `STEP15 EXEC PGM=CBTRN02C`. AWS: state machine
  `jobs/cbtrn02c/deploy/POSTTRAN.asl.json`, job definition `jobs/cbtrn02c/deploy/batch-job-definition.json`.
* DDs: `DALYTRAN`, `XREFFILE`, `ACCTFILE`, `TCATBALF`, `TRANFILE`, `DALYREJS`, `SYSOUT`.
* Upstream: `DALYVRFY` (`CBTRN01C`, daily verification). Downstream: `TRANREPT` (`CBTRN03C`) reads the posted
  transactions. Daily chain `DALYVRFY` → `POSTTRAN` → `TRANREPT`.

## 2. Inputs and outputs

| DD | Copybook | Organization / COBOL access | Java | Dataset |
| --- | --- | --- | --- | --- |
| DALYTRAN | CVTRA06Y (350) | PS, sequential INPUT | `ctx.input("DALYTRAN")`, `FixedWidthFile` | – |
| XREFFILE | CVACT03Y (50) | KSDS, random INPUT, key XREF-CARD-NUM | `ctx.input("XREFFILE")`, `KeyedFile` | – |
| ACCTFILE | CVACT01Y (300) | KSDS, random I-O, key ACCT-ID | `ctx.input("ACCTFILE")`, `KeyedFile` | `account_master` (Parquet; whole file after posting, key order) |
| TCATBALF | CVTRA01Y (50) | KSDS, random I-O, key TRANCAT-ACCT-ID + TRANCAT-TYPE-CD + TRANCAT-CD | `ctx.input("TCATBALF")`, `KeyedFile` | `tran_cat_balance` (Parquet; whole file after posting, key order) |
| TRANFILE | CVTRA05Y (350) | KSDS, random OUTPUT (starts empty), key TRAN-ID | empty `KeyedFile` | `posted_transactions` (Parquet; key order) |
| DALYREJS | 430 = DALYTRAN image + 80-byte trailer | PS OUTPUT | `ctx.openDataset` | `daily_rejects` (Parquet; write order) |

`daily_rejects` columns are the CVTRA06Y columns plus `validation_fail_reason` (`9(04)`, string `"0102"`) and
`validation_fail_reason_desc` (`X(76)`, trimmed). Schema contracts: `src/main/resources/schemas/*.schema.json`.

## 3. Processing rules

* **BR-CBTRN02C-01** (`PROCEDURE DIVISION` main loop, `1000-DALYTRAN-GET-NEXT`): read DALYTRAN sequentially until
  status 10. For each record: `ADD 1 TO WS-TRANSACTION-COUNT`, reset reason to 0 and description to spaces,
  `PERFORM 1500-VALIDATE-TRAN`; reason 0 → `2000-POST-TRANSACTION`, else `ADD 1 TO WS-REJECT-COUNT` and
  `2500-WRITE-REJECT-REC`.
* **BR-CBTRN02C-02** (`1500-A-LOOKUP-XREF`): READ XREFFILE by `DALYTRAN-CARD-NUM`; INVALID KEY → reason 100
  `INVALID CARD NUMBER FOUND`. The account lookup is skipped when the reason is non-zero.
* **BR-CBTRN02C-03** (`1500-B-LOOKUP-ACCT`): READ ACCTFILE by `XREF-ACCT-ID`; INVALID KEY → reason 101
  `ACCOUNT RECORD NOT FOUND`, and the limit/expiry checks are skipped.
* **BR-CBTRN02C-04** (`1500-B-LOOKUP-ACCT`): `COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT +
  DALYTRAN-AMT` into `S9(09)V99` (high-order digits truncated, no ON SIZE ERROR). If `ACCT-CREDIT-LIMIT >=
  WS-TEMP-BAL` OK, else reason 102 `OVERLIMIT TRANSACTION`. Equality is accepted.
* **BR-CBTRN02C-05** (`1500-B-LOOKUP-ACCT`): if `ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS(1:10)` OK (alphanumeric
  compare of `YYYY-MM-DD`), else reason 103 `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`. Evaluated after 102, so
  103 overwrites 102.
* **BR-CBTRN02C-06** (`2000-POST-TRANSACTION`): build TRAN-RECORD by moving DALYTRAN ID, type, category, source,
  description, amount, merchant id/name/city/zip, card number and original timestamp; `TRAN-PROC-TS` from
  `Z-GET-DB2-FORMAT-TIMESTAMP`. Then 2700, 2800, 2900 in that order.
* **BR-CBTRN02C-07** (`Z-GET-DB2-FORMAT-TIMESTAMP`): `FUNCTION CURRENT-DATE` → `YYYY-MM-DD-HH.MM.SS.hh0000`
  (hundredths, then literal `0000`). Java uses `ctx.clock()` in UTC.
* **BR-CBTRN02C-08** (`2700-UPDATE-TCATBAL`): READ TCATBALF by `XREF-ACCT-ID + DALYTRAN-TYPE-CD + DALYTRAN-CAT-CD`;
  INVALID KEY (status 23) → DISPLAY `TCATBAL record not found for key : <17-byte key>.. Creating.` and create.
* **BR-CBTRN02C-09** (`2700-A-CREATE-TCATBAL-REC`): initialize the record, set the key, `ADD DALYTRAN-AMT TO
  TRAN-CAT-BAL`, WRITE.
* **BR-CBTRN02C-10** (`2700-B-UPDATE-TCATBAL-REC`): `ADD DALYTRAN-AMT TO TRAN-CAT-BAL` (S9(09)V99), REWRITE.
* **BR-CBTRN02C-11** (`2800-UPDATE-ACCOUNT-REC`): `ADD DALYTRAN-AMT TO ACCT-CURR-BAL`; if `DALYTRAN-AMT >= 0` add it
  to `ACCT-CURR-CYC-CREDIT`, else to `ACCT-CURR-CYC-DEBIT` (so debits are stored negative); REWRITE. Later
  transactions of the same run see the updated balances.
* **BR-CBTRN02C-12** (`2900-WRITE-TRANSACTION-FILE`): WRITE TRAN-RECORD to TRANFILE (keyed by TRAN-ID).
* **BR-CBTRN02C-13** (`2500-WRITE-REJECT-REC`): write `DALYTRAN-RECORD` + `WS-VALIDATION-FAIL-REASON` (4 digits) +
  description (76 bytes).
* **BR-CBTRN02C-14** (main procedure, end): DISPLAY `TRANSACTIONS PROCESSED :` and `TRANSACTIONS REJECTED  :` with
  9-digit counters; `RETURN-CODE` 4 if `WS-REJECT-COUNT > 0`, else 0. Java counters `transactions_processed`,
  `transactions_rejected`. SYSOUT starts with `START OF EXECUTION OF PROGRAM CBTRN02C` and ends with
  `END OF EXECUTION OF PROGRAM CBTRN02C`.

## 4. Error handling

All abends go through `9910-DISPLAY-IO-STATUS` (`FILE STATUS IS: NNNN00ss`) and `9999-ABEND-PROGRAM`
(`ABENDING PROGRAM`, `CALL 'CEE3ABD'` with code 999) → Java `AbendException(999)`, process exit 99. No datasets
are published on abend (staged outputs are not finalized).

| Paragraph | Condition | Message |
| --- | --- | --- |
| `0000`–`0500-*-OPEN` | status ≠ 00 (Java: DD missing or not a file → status 35) | `ERROR OPENING DALYTRAN` / `TRANSACTION FILE` / `CROSS REF FILE` / `DALY REJECTS FILE` / `ACCOUNT MASTER FILE` / `TRANSACTION BALANCE FILE` |
| `1000-DALYTRAN-GET-NEXT` | status not 00/10 | `ERROR READING DALYTRAN FILE` |
| `2500-WRITE-REJECT-REC` | status ≠ 00 | `ERROR WRITING TO REJECTS FILE` |
| `2700-UPDATE-TCATBAL` | status not 00/23 | `ERROR READING TRANSACTION BALANCE FILE` |
| `2700-A-CREATE-TCATBAL-REC` | WRITE status ≠ 00 | `ERROR WRITING TRANSACTION BALANCE FILE` |
| `2700-B-UPDATE-TCATBAL-REC` | REWRITE status ≠ 00 | `ERROR REWRITING TRANSACTION BALANCE FILE` |
| `2900-WRITE-TRANSACTION-FILE` | WRITE status ≠ 00 (duplicate TRAN-ID → 22) | `ERROR WRITING TO TRANSACTION FILE` |
| `9000`–`9500-*-CLOSE` | status ≠ 00 | `ERROR CLOSING ...` |

INVALID KEY on XREFFILE/ACCTFILE READ is a business reject (100/101), not an error. INVALID KEY on ACCTFILE REWRITE
in `2800` only sets reason 109 (see quirks). In memory, read/close of the Java files cannot fail, so the read and
close abend branches are kept structurally but are only reachable through missing inputs.

## 5. Quirks preserved for parity

* **Q1 – 103 overwrites 102** (`1500-B-LOOKUP-ACCT`): both checks run; the reject carries only the last reason.
* **Q2 – WS-TEMP-BAL truncation** (`1500-B-LOOKUP-ACCT`, `PIC S9(09)V99`): an intermediate of 10000000000.00 becomes
  0.00, so a hugely overlimit account passes; 1000000051.00 becomes 51.00. Verified under GnuCOBOL
  (`picture-truncation`). Java: `Cbtrn02cJob.picture(v, 9, 2)`.
* **Q3 – ADD wraps balances** (`2700-*`, `2800`): `ADD` without ON SIZE ERROR truncates high-order digits:
  ACCT-* `S9(10)V99` (9999999999.50 + 1.00 → 0.50), TRAN-CAT-BAL `S9(09)V99` (999999999.99 + 1.00 → 0.99).
* **Q4 – debits make the limit check stricter** (`1500-B`, `2800`): CYC-DEBIT holds negative amounts, so
  `CREDIT - DEBIT` *increases* with every debit; a later small credit can be rejected 0102 (`negative-amounts`).
* **Q5 – zero amount is a credit** (`2800`): `DALYTRAN-AMT >= 0`.
* **Q6 – reason 109 is dead** (`2800-UPDATE-ACCOUNT-REC`): REWRITE INVALID KEY moves 109 to the reason after
  validation; nothing reads it, no reject is written, the transaction is still written. Reproduced; unreachable in
  practice because the record was just read.
* **Q7 – no rollback on abend** (`2900`): a duplicate TRAN-ID abends after TCATBAL and the account were already
  updated. On z/OS the VSAM updates of earlier records persist; the Java job publishes no datasets on abend, so a
  rerun starts from the original inputs. Deliberate difference: output is all-or-nothing.
* **Q8 – processing timestamp**: one `CURRENT-DATE` per posted transaction; Java reads `ctx.clock()` each time, so
  with a fixed clock all records share one value (as in the fixtures).
* **Q9 – expiry compare is alphanumeric** on `YYYY-MM-DD`; an equal date is accepted.
* Not reproduced: physical TRANFILE/DALYREJS/VSAM writes; outputs are the four datasets above (design §5). The
  reject trailer is split into two typed columns instead of an 80-byte FILLER.

## 6. Traceability

Methods are on `Cbtrn02cJob.Run` unless noted. Tests: `P` = `Cbtrn02cParityTest`, `J` = `Cbtrn02cJobTest`,
`S` = `Cbtrn02cSchemaTest`.

| Rule | Java | Tests |
| --- | --- | --- |
| BR-CBTRN02C-01 | `procedureDivision`, `dalytranGetNext` | P.`sampleData`, P.`edgeCase[*]` |
| BR-CBTRN02C-02 | `validateTran`, `lookupXref` | P.`edgeCase[reject-reasons]` |
| BR-CBTRN02C-03 | `lookupAcct` | P.`edgeCase[reject-reasons]` |
| BR-CBTRN02C-04 | `lookupAcct`, `Cbtrn02cJob.picture` | P.`sampleData`, P.`edgeCase[reject-reasons, negative-amounts, picture-truncation]`, J.`wsTempBalPictureTruncation` |
| BR-CBTRN02C-05 | `lookupAcct` | P.`edgeCase[reject-reasons]` |
| BR-CBTRN02C-06 | `postTransaction` | P.`sampleData`, P.`edgeCase[*]` |
| BR-CBTRN02C-07 | `getDb2FormatTimestamp`, `Cbtrn02cJob.db2FormatTimestamp` | J.`db2Timestamp`, P.`sampleData` |
| BR-CBTRN02C-08 | `updateTcatbal` | P.`sampleData`, P.`edgeCase[tcatbal-create-update]` |
| BR-CBTRN02C-09 | `createTcatbalRec` | P.`edgeCase[tcatbal-create-update]` |
| BR-CBTRN02C-10 | `updateTcatbalRec` | P.`edgeCase[tcatbal-create-update, picture-truncation]` |
| BR-CBTRN02C-11 | `updateAccountRec` | P.`edgeCase[negative-amounts, picture-truncation]` |
| BR-CBTRN02C-12 | `writeTransactionFile`, `tranfileClose` | P.`sampleData`, P.`duplicateTranIdAbends` |
| BR-CBTRN02C-13 | `writeRejectRec`, `Cbtrn02cSchemas.dailyRejects` | P.`edgeCase[reject-reasons]`, S.`checkedInContractMatches` |
| BR-CBTRN02C-14 | `procedureDivision`, `Cbtrn02cJob.counter` | P.`sampleData`, P.`rejectReasonsCountersAndReturnCode`, P.`noRejectsGivesReturnCodeZero` |
| Error handling | `openInput`, `ioError`, `displayIoStatus`, `abendProgram` | P.`duplicateTranIdAbends`, P.`missingAccountFileAbendsAtOpen` |
| Outputs/schemas | `acctfileClose`, `tcatbalfClose`, `Cbtrn02cSchemas` | S.`checkedInContractMatches[*]`, S.`publishedSchemasEqualContracts` |
| Registration | `META-INF/services` | J.`registeredWithServiceLoader` |
