# COBOL -> Java Traceability Matrix: CBACT04C -> interest-service

Source program: `app/cbl/CBACT04C.cbl` (interest calculator, JCL `app/jcl/INTCALC.jcl` STEP15, line 22
`EXEC PGM=CBACT04C,PARM='2022071800'`).
Java module: `java/interest-service`, base package `com.carddemo.interest`.

Every Java element listed here also carries a `@com.carddemo.interest.trace.Trace` annotation with
the same program/copybook, paragraph and line range. Line numbers refer to the files in `app/cbl`
and `app/cpy` on the `main` branch (verified with `cat -n` against commit `59cc6c2`).

## Conventions

- `@Trace(program = "CBACT04C", paragraph = "<PARAGRAPH>", lines = "<from>-<to>")` for procedure logic.
- `@Trace(copybook = "<COPYBOOK>", lines = "<from>-<to>")` for record layouts.
- Test methods are named after the COBOL paragraph they validate, e.g. `paragraph1300ComputeInterest_truncatesNotRounds`.
- All `V99` fields map to `java.math.BigDecimal` with `scale = 2`; unsigned `PIC 9(n)` identifiers/keys
  (account id, category code, merchant id) are kept as zero-padded `String` so the fixed-width record
  round-trips byte-for-byte. `PIC X` fields are `String`, space-padded to the PIC length on `toRecord()`.
- Signed fields in the ASCII data use zoned-decimal overpunch on the last digit (`{`=+0, `}`=-0,
  `A`-`I`=+1..+9, `J`-`R`=-1..-9); `fromRecord`/`toRecord` in each domain record own that encoding.
- COBOL `COMPUTE` without `ROUNDED` truncates -> `RoundingMode.DOWN`.

## 1. Copybooks -> domain records

| Copybook | COBOL structure | Lines (`app/cpy`) | RECLN | Java type | Java `RECORD_LENGTH` | Notes |
|---|---|---|---|---|---|---|
| CVTRA01Y | TRAN-CAT-BAL-RECORD | 4-10 | 50 | `domain.TranCatBalance` | 50 | Read sequentially by 1000-TCATBALF-GET-NEXT (KSDS key = TRAN-CAT-KEY) |
| CVTRA02Y | DIS-GROUP-RECORD | 4-10 | 50 | `domain.DisclosureGroup` | 50 | Read randomly by 1200 / 1200-A (key = DIS-GROUP-KEY) |
| CVACT01Y | ACCOUNT-RECORD | 4-17 | 300 | `domain.Account` | 300 | Read by 1100, REWRITE by 1050 |
| CVACT03Y | CARD-XREF-RECORD | 4-8 | 50 | `domain.CardXref` | 50 | Read via alternate index FD-XREF-ACCT-ID by 1110 |
| CVTRA05Y | TRAN-RECORD | 4-18 | 350 | `domain.Transaction` | 350 | Written by 1300-B-WRITE-TX |

Copybook line ranges above are the ones the `@Trace(copybook=...)` annotations must carry (see
section 6 for the discrepancy with the scaffold stubs).

### 1.1 CVTRA01Y `TRAN-CAT-BAL-RECORD` (lines 4-10) -> `TranCatBalance(acctId, typeCd, catCd, balance)`

| COBOL field | Line | PIC | Offset (0-based) | Java component | Java type | Scale |
|---|---|---|---|---|---|---|
| TRAN-CAT-KEY | 5 | group (17 bytes) | 0-16 | `acctId` + `typeCd` + `catCd` | - | - |
| TRANCAT-ACCT-ID | 6 | 9(11) | 0-10 | `acctId` | `String` (11, zero-padded) | - |
| TRANCAT-TYPE-CD | 7 | X(02) | 11-12 | `typeCd` | `String` (2) | - |
| TRANCAT-CD | 8 | 9(04) | 13-16 | `catCd` | `String` (4, zero-padded) | - |
| TRAN-CAT-BAL | 9 | S9(09)V99 | 17-27 | `balance` | `BigDecimal` | 2 |
| FILLER | 10 | X(22) | 28-49 | (none; spaces on `toRecord`) | - | - |

### 1.2 CVTRA02Y `DIS-GROUP-RECORD` (lines 4-10) -> `DisclosureGroup(groupId, typeCd, catCd, intRate)`

| COBOL field | Line | PIC | Offset | Java component | Java type | Scale |
|---|---|---|---|---|---|---|
| DIS-GROUP-KEY | 5 | group (16 bytes) | 0-15 | `groupId` + `typeCd` + `catCd` | - | - |
| DIS-ACCT-GROUP-ID | 6 | X(10) | 0-9 | `groupId` | `String` (10, space-padded); `DisclosureGroup.DEFAULT_GROUP_ID = "DEFAULT"` | - |
| DIS-TRAN-TYPE-CD | 7 | X(02) | 10-11 | `typeCd` | `String` (2) | - |
| DIS-TRAN-CAT-CD | 8 | 9(04) | 12-15 | `catCd` | `String` (4, zero-padded) | - |
| DIS-INT-RATE | 9 | S9(04)V99 | 16-21 | `intRate` | `BigDecimal` | 2 |
| FILLER | 10 | X(28) | 22-49 | (none) | - | - |

### 1.3 CVACT01Y `ACCOUNT-RECORD` (lines 4-17) -> `Account(...)`

| COBOL field | Line | PIC | Offset | Java component | Java type | Scale |
|---|---|---|---|---|---|---|
| ACCT-ID | 5 | 9(11) | 0-10 | `id` | `String` (11) | - |
| ACCT-ACTIVE-STATUS | 6 | X(01) | 11 | `activeStatus` | `String` (1) | - |
| ACCT-CURR-BAL | 7 | S9(10)V99 | 12-23 | `currBal` | `BigDecimal` | 2 |
| ACCT-CREDIT-LIMIT | 8 | S9(10)V99 | 24-35 | `creditLimit` | `BigDecimal` | 2 |
| ACCT-CASH-CREDIT-LIMIT | 9 | S9(10)V99 | 36-47 | `cashCreditLimit` | `BigDecimal` | 2 |
| ACCT-OPEN-DATE | 10 | X(10) | 48-57 | `openDate` | `String` (10) | - |
| ACCT-EXPIRAION-DATE (sic) | 11 | X(10) | 58-67 | `expirationDate` | `String` (10) | - |
| ACCT-REISSUE-DATE | 12 | X(10) | 68-77 | `reissueDate` | `String` (10) | - |
| ACCT-CURR-CYC-CREDIT | 13 | S9(10)V99 | 78-89 | `currCycCredit` | `BigDecimal` | 2 |
| ACCT-CURR-CYC-DEBIT | 14 | S9(10)V99 | 90-101 | `currCycDebit` | `BigDecimal` | 2 |
| ACCT-ADDR-ZIP | 15 | X(10) | 102-111 | `addrZip` | `String` (10) | - |
| ACCT-GROUP-ID | 16 | X(10) | 112-121 | `groupId` | `String` (10) | - |
| FILLER | 17 | X(178) | 122-299 | (none) | - | - |

Only `currBal`, `currCycCredit`, `currCycDebit` are mutated by CBACT04C (1050-UPDATE-ACCOUNT, lines
352-354) -> `Account.withBalances(currBal, currCycCredit, currCycDebit)`.

### 1.4 CVACT03Y `CARD-XREF-RECORD` (lines 4-8) -> `CardXref(cardNum, custId, acctId)`

| COBOL field | Line | PIC | Offset | Java component | Java type | Scale |
|---|---|---|---|---|---|---|
| XREF-CARD-NUM | 5 | X(16) | 0-15 | `cardNum` | `String` (16) | - |
| XREF-CUST-ID | 6 | 9(09) | 16-24 | `custId` | `String` (9) | - |
| XREF-ACCT-ID | 7 | 9(11) | 25-35 | `acctId` | `String` (11) | - |
| FILLER | 8 | X(14) | 36-49 | (none) | - | - |

CBACT04C reads this file `KEY IS FD-XREF-ACCT-ID` (alternate index, line 395); only `XREF-CARD-NUM`
is consumed (line 495). `InMemoryCardXrefRepository.load` keeps the first xref per account
(`putIfAbsent`), mirroring a VSAM AIX read that returns the first duplicate.

### 1.5 CVTRA05Y `TRAN-RECORD` (lines 4-18) -> `Transaction(...)`

| COBOL field | Line | PIC | Offset | Java component | Java type | Scale |
|---|---|---|---|---|---|---|
| TRAN-ID | 5 | X(16) | 0-15 | `id` | `String` (16) | - |
| TRAN-TYPE-CD | 6 | X(02) | 16-17 | `typeCd` | `String` (2) | - |
| TRAN-CAT-CD | 7 | 9(04) | 18-21 | `catCd` | `String` (4) | - |
| TRAN-SOURCE | 8 | X(10) | 22-31 | `source` | `String` (10) | - |
| TRAN-DESC | 9 | X(100) | 32-131 | `description` | `String` (100) | - |
| TRAN-AMT | 10 | S9(09)V99 | 132-142 | `amount` | `BigDecimal` | 2 |
| TRAN-MERCHANT-ID | 11 | 9(09) | 143-151 | `merchantId` | `String` (9, zero-padded) | - |
| TRAN-MERCHANT-NAME | 12 | X(50) | 152-201 | `merchantName` | `String` (50) | - |
| TRAN-MERCHANT-CITY | 13 | X(50) | 202-251 | `merchantCity` | `String` (50) | - |
| TRAN-MERCHANT-ZIP | 14 | X(10) | 252-261 | `merchantZip` | `String` (10) | - |
| TRAN-CARD-NUM | 15 | X(16) | 262-277 | `cardNum` | `String` (16) | - |
| TRAN-ORIG-TS | 16 | X(26) | 278-303 | `origTs` | `String` (26) | - |
| TRAN-PROC-TS | 17 | X(26) | 304-329 | `procTs` | `String` (26) | - |
| FILLER | 18 | X(20) | 330-349 | (none) | - | - |

## 2. Paragraphs -> Java classes / methods

Files/DDs in CBACT04C and their Java counterparts (FILE-CONTROL lines 28-56, FD lines 61-92):

| COBOL file (DD) | Lines | Access | Java counterpart | Job parameter |
|---|---|---|---|---|
| TCATBAL-FILE (TCATBALF) | 28-32, 61-67 | INDEXED / SEQUENTIAL | Spring Batch `ItemReader<TranCatBalance>` (flat-file reader over `TranCatBalance.fromRecord`) in `batch.InterestJobConfig` | `InterestJobConfig.PARAM_TCATBAL_FILE` |
| XREF-FILE (XREFFILE) | 34-39, 69-74 | INDEXED / RANDOM, AIX on FD-XREF-ACCT-ID | `repository.CardXrefRepository` (`InMemoryCardXrefRepository`) | `PARAM_XREF_FILE` |
| ACCOUNT-FILE (ACCTFILE) | 41-45, 84-87 | INDEXED / RANDOM, I-O | `repository.AccountRepository` (`InMemoryAccountRepository`) | `PARAM_ACCT_FILE` |
| DISCGRP-FILE (DISCGRP) | 47-51, 76-82 | INDEXED / RANDOM | `repository.DisclosureGroupRepository` (`InMemoryDisclosureGroupRepository`) | `PARAM_DISCGRP_FILE` |
| TRANSACT-FILE (TRANSACT) | 53-56, 89-92 | SEQUENTIAL OUTPUT | Spring Batch `ItemWriter<InterestItemResult>` in `batch.InterestJobConfig` writing `Transaction.toRecord()` and applying `AccountRepository.rewrite` | `PARAM_TRAN_FILE` |

| COBOL paragraph | Lines | Java element | Semantics notes |
|---|---|---|---|
| PROCEDURE DIVISION entry / opens | 180-186 | `batch.InterestJobConfig` job/step definition (`JOB_NAME = "interestCalcJob"`); `USING EXTERNAL-PARMS` -> job parameter `PARAM_RUN_DATE` | `DISPLAY 'START OF EXECUTION...'` (181) -> logging only |
| PROCEDURE DIVISION main loop | 188-222 | `batch.InterestProcessor.process(TranCatBalance)` (one call per TCATBAL record, replaces `PERFORM UNTIL END-OF-FILE`) | `ADD 1 TO WS-RECORD-COUNT` (192) -> processor counter; `DISPLAY TRAN-CAT-BAL-RECORD` (193) -> debug log |
| control-break block | 194-206 | `InterestProcessor.process`: `if (!item.acctId().equals(lastAcctNum))` | On account change: if not first time, post the *previous* account (`AccountPostingService.postInterest` + returned in `InterestItemResult.accountUpdates`), else clear first-time flag (195-199); reset running total to 0 (200); remember account (201); load account (`AccountRepository.findById`, 202-203) and xref (`CardXrefRepository.findByAcctId`, 204-205). The account posted at the break is the one read at the previous break, so the processor must retain the previous `Account`. |
| disclosure key build + rate lookup | 210-213 | `InterestRateService.getRate(account.groupId(), item.typeCd(), item.catCd())` | Key = ACCT-GROUP-ID of the *current* account + TRANCAT-TYPE-CD + TRANCAT-CD |
| rate == 0 guard | 214-217 | `InterestProcessor.process`: `if (rate.signum() != 0)` | Only when `DIS-INT-RATE NOT = 0`: compute interest (1300) and fees (1400). Zero-rate records produce no transaction and do not touch WS-TOTAL-INT, but the account is still rewritten at the control break / EOF with WS-TOTAL-INT = 0 added and cycle credit/debit zeroed. |
| EOF branch | 219-221 | `batch.InterestProcessor.flush()`, invoked from `InterestStepListener.afterStep` only when job parameter `postFinalAccount=true` | **Dead code in CBACT04C**: `PERFORM UNTIL END-OF-FILE = 'Y'` (188) exits before the `ELSE` can run, so the last account's balance is never posted (confirmed by the GnuCOBOL golden files). Default Java behaviour reproduces that; see section 6 item 4. |
| closes / end | 224-232 | Spring Batch step completion; `DISPLAY 'END OF EXECUTION...'` (230) -> log | `GOBACK` (232) -> `InterestServiceApplication.main` `System.exit(SpringApplication.exit(...))` |
| 0000-TCATBALF-OPEN | 234-250 | `ItemReader.open()` / resource existence check in `InterestJobConfig` | non-'00' status -> `InterestServiceException` |
| 0100-XREFFILE-OPEN | 252-268 | `InMemoryCardXrefRepository.load(...)` from `PARAM_XREF_FILE` | idem |
| 0200-DISCGRP-OPEN | 270-286 | `InMemoryDisclosureGroupRepository.load(...)` from `PARAM_DISCGRP_FILE` | idem; COBOL message text says 'DALY REJECTS FILE' (281) - copy/paste error in source |
| 0300-ACCTFILE-OPEN | 289-305 | `InMemoryAccountRepository.load(...)` from `PARAM_ACCT_FILE` | `OPEN I-O` -> repository supports `rewrite` |
| 0400-TRANFILE-OPEN | 307-323 | `ItemWriter.open()` for `PARAM_TRAN_FILE` (`OPEN OUTPUT` -> truncate/create) | idem |
| 1000-TCATBALF-GET-NEXT | 325-348 | `ItemReader<TranCatBalance>.read()`; `null` return == status '10' (EOF, lines 330-331, 339-340) | status other than '00'/'10' -> `InterestServiceException` (342-345) |
| 1050-UPDATE-ACCOUNT | 350-370 | `service.AccountPostingService.postInterest(Account, BigDecimal totalInterest)` (lines 352-354) returning `Account.withBalances(currBal + totalInterest, 0, 0)`; REWRITE (356) -> `AccountRepository.rewrite(Account)` performed by the writer | `ADD WS-TOTAL-INT TO ACCT-CURR-BAL` has no `ON SIZE ERROR`: result exceeding S9(10)V99 is truncated on the left (high-order digits lost) - Java must either mirror by `remainder(10^10)` or fail fast; see section 6. Cycle credit/debit always reset to 0. |
| 1100-GET-ACCT-DATA | 372-391 | `repository.AccountRepository.findById(acctId)`; empty `Optional` -> `InterestServiceException` | `INVALID KEY` only DISPLAYs (375); the following status check (378-389) abends on any non-'00', so a missing account is fatal |
| 1110-GET-XREF-DATA | 393-413 | `repository.CardXrefRepository.findByAcctId(acctId)`; empty -> `InterestServiceException` | same pattern; read by alternate key (395) |
| 1200-GET-INTEREST-RATE | 415-440 | `service.InterestRateService.getRate(groupId, typeCd, catCd)` -> `DisclosureGroupRepository.findByKey(groupId, typeCd, catCd)` | Status '00' or '23' (record not found) are both OK (422); any other status abends (431-434) |
| status-23 DEFAULT fallback | 436-439 | `InterestRateService.getRate`: on empty `Optional`, retry with `DisclosureGroup.DEFAULT_GROUP_ID` | `MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID` keeps the same type/category codes |
| 1200-A-GET-DEFAULT-INT-RATE | 443-460 | `InterestRateService.getRate` second `findByKey("DEFAULT", typeCd, catCd)`; empty -> `InterestServiceException` | No `INVALID KEY` clause; any non-'00' (including '23') abends (455-458) |
| 1300-COMPUTE-INTEREST | 462-470 | `calc.InterestCalculator.computeMonthlyInterest(balance, annualRate)` (464-465) then `InterestCalculator.accumulate(runningTotal, monthlyInterest)` (467), then `TransactionFactory.interestTransaction` (468) | `COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200` without `ROUNDED`: intermediate product has scale 4, quotient truncated to scale 2 -> `multiply(rate).divide(MONTHLY_DIVISOR, SCALE, RoundingMode.DOWN)`. Result stored into S9(09)V99: magnitude > 9,999,999,999.99 is truncated high-order (no SIZE ERROR). `ADD` (467) likewise truncates on overflow. |
| 1300-B-WRITE-TX | 473-515 | `service.TransactionFactory.interestTransaction(runDate, suffix, account, xref, monthlyInterest, timestamp)`; the `WRITE` (500) -> `ItemWriter` | Per-MOVE mapping in table 2.1. Status non-'00' on WRITE -> `InterestServiceException` (510-513) |
| 1400-COMPUTE-FEES | 518-520 | `service.AccountPostingService.computeFees(Account)` | COBOL body is `* To be implemented` + `EXIT`: Java implementation is a documented no-op; keep it called only inside the rate != 0 guard (216) |
| 9000-TCATBALF-CLOSE | 522-538 | `ItemReader.close()` | close error -> `InterestServiceException` |
| 9100-XREFFILE-CLOSE | 541-557 | (no-op; in-memory repository) | |
| 9200-DISCGRP-CLOSE | 559-575 | (no-op; in-memory repository) | |
| 9300-ACCTFILE-CLOSE | 577-593 | persist `InMemoryAccountRepository.findAll()` to `PARAM_ACCT_FILE` at step end (equivalent of closing the I-O KSDS) | |
| 9400-TRANFILE-CLOSE | 595-611 | `ItemWriter.close()` | |
| Z-GET-DB2-FORMAT-TIMESTAMP | 613-626 | `Db2TimestampSupplier` (`java.util.function.Supplier<String>` over `java.time`, injected into `InterestJobConfig` and passed to `TransactionFactory.interestTransaction` as `timestamp`) | Format `yyyy-MM-dd-HH.mm.ss.SS0000` (26 chars: COB-MIL is 2 digits = hundredths, DB2-REST is literal `0000`, lines 621-622). Java: `DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS'0000'")`. Non-deterministic in COBOL; the Java supplier is injectable so tests/golden runs can pin it. |
| 9999-ABEND-PROGRAM | 628-632 | `service.InterestServiceException` (thrown; Spring Batch marks step FAILED, `main` exits non-zero) | `CALL 'CEE3ABD' USING ABCODE(999), TIMING(0)` = user abend U0999 with no dump. Java has no abend code; the exception message carries the COBOL DISPLAY text and the file status. |
| 9910-DISPLAY-IO-STATUS | 635-648 | message-formatting helper inside `InterestServiceException` (or a static `ioStatus(String)` on it) | Formats a 2-char VSAM status into 4 digits (`0000` + status, or `9`+binary of STAT2 for non-numeric/'9x' statuses). Java repositories/readers do not surface VSAM statuses; see section 6. |

### 2.1 1300-B-WRITE-TX per-MOVE mapping (lines 474-498) -> `Transaction` components

| Line(s) | COBOL statement | Target field (PIC) | `Transaction` component / `TransactionFactory` constant | Semantics notes |
|---|---|---|---|---|
| 474 | `ADD 1 TO WS-TRANID-SUFFIX` | WS-TRANID-SUFFIX 9(06) | `suffix` argument (`long`), maintained in `InterestProcessor` | Program-wide counter, *not* reset per account. Wraps silently at 1,000,000 (ADD truncation). |
| 476-480 | `STRING PARM-DATE, WS-TRANID-SUFFIX DELIMITED BY SIZE INTO TRAN-ID` | TRAN-ID X(16) | `id` = `runDate` (10 chars) + `String.format("%06d", suffix)` | PARM-DATE is X(10) = `'2022071800'` from JCL; suffix is unsigned zoned 9(06) => 6 ASCII digits, zero-padded; 10 + 6 = 16 exactly fills TRAN-ID. First transaction id of a run is `2022071800000001`. |
| 482 | `MOVE '01' TO TRAN-TYPE-CD` | X(02) | `typeCd` = `INTEREST_TRAN_TYPE_CD = "01"` | |
| 483 | `MOVE '05' TO TRAN-CAT-CD` | 9(04) | `catCd` = `INTEREST_TRAN_CAT_CD = "0005"` | Alphanumeric-to-numeric MOVE: `'05'` is right-justified and zero-filled into 9(04) -> `0005`. |
| 484 | `MOVE 'System' TO TRAN-SOURCE` | X(10) | `source` = `INTEREST_TRAN_SOURCE = "System"` | Space-padded to 10 on `toRecord()`. |
| 485-489 | `STRING 'Int. for a/c ', ACCT-ID DELIMITED BY SIZE INTO TRAN-DESC` | X(100) | `description` = `INTEREST_DESC_PREFIX + account.id()` | `STRING ... INTO` does **not** space-fill the remainder of the receiving field; TRAN-DESC is only ever written by this STRING in a freshly opened WORKING-STORAGE area with no VALUE clause. On IBM Enterprise COBOL WORKING-STORAGE without VALUE is initialised per the `WSCLEAR`/default rules (binary zeros or spaces depending on compile options). The Java `toRecord()` space-pads; the golden-file parity test must confirm bytes 32-131 beyond position 55 (13 + 11 = 24 chars) are spaces in the mainframe output. |
| 490 | `MOVE WS-MONTHLY-INT TO TRAN-AMT` | S9(09)V99 | `amount` = `monthlyInterest` (scale 2) | Same PIC as WS-MONTHLY-INT, no truncation. |
| 491 | `MOVE 0 TO TRAN-MERCHANT-ID` | 9(09) | `merchantId` = `"000000000"` | |
| 492 | `MOVE SPACES TO TRAN-MERCHANT-NAME` | X(50) | `merchantName` = `""` (padded to 50 spaces) | |
| 493 | `MOVE SPACES TO TRAN-MERCHANT-CITY` | X(50) | `merchantCity` = `""` | |
| 494 | `MOVE SPACES TO TRAN-MERCHANT-ZIP` | X(10) | `merchantZip` = `""` | |
| 495 | `MOVE XREF-CARD-NUM TO TRAN-CARD-NUM` | X(16) | `cardNum` = `xref.cardNum()` | From the xref read at the control break (1110). |
| 496-497 | `PERFORM Z-GET-DB2-FORMAT-TIMESTAMP` / `MOVE DB2-FORMAT-TS TO TRAN-ORIG-TS` | X(26) | `origTs` = `timestamp` | Supplier invoked once per transaction. |
| 498 | `MOVE DB2-FORMAT-TS TO TRAN-PROC-TS` | X(26) | `procTs` = `timestamp` (same value as `origTs`) | |
| 500 | `WRITE FD-TRANFILE-REC FROM TRAN-RECORD` | 350 bytes | `Transaction.toRecord()` via `ItemWriter` | Fields not MOVEd by the program (FILLER 20 bytes) are whatever WORKING-STORAGE held; Java writes spaces. |

## 3. Working-storage -> Java state

| COBOL item | Lines | PIC / VALUE | Java element | Notes |
|---|---|---|---|---|
| WS-LAST-ACCT-NUM | 167 | X(11) VALUE SPACES | `InterestProcessor` field `String lastAcctNum` (initially `null`/spaces) | Control-break key (194). Compared as 11-char string, so the first record always differs (spaces != digits). |
| WS-MONTHLY-INT | 168 | S9(09)V99 (no VALUE) | local `BigDecimal monthlyInterest` in `InterestProcessor.process` (return value of `InterestCalculator.computeMonthlyInterest`) | Scale 2. |
| WS-TOTAL-INT | 169 | S9(09)V99 (no VALUE) | `InterestProcessor` field `BigDecimal totalInt` | Zeroed at every control break (200); never explicitly initialised before the first break (VALUE absent) - first break sets it to 0 before any ADD, so effectively 0. Passed to `AccountPostingService.postInterest`. |
| WS-FIRST-TIME | 170 | X(01) VALUE 'Y' | `InterestProcessor` field `boolean firstTime = true` | Set false at the first control break (198); prevents posting a not-yet-read account. `flush()` must post only if `!firstTime` (see section 6 for the COBOL divergence). |
| WS-RECORD-COUNT | 172 | 9(09) VALUE 0 | `InterestProcessor` field `long recordCount` (or Spring Batch `StepExecution.readCount`) | Informational only; never displayed or written by the program. |
| WS-TRANID-SUFFIX | 173 | 9(06) VALUE 0 | `InterestProcessor` field `long tranIdSuffix` passed as `suffix` to `TransactionFactory.interestTransaction` | Incremented before use (474): first suffix is 000001. Program-global across accounts. |
| END-OF-FILE | 137 | X(01) VALUE 'N' | implicit: `ItemReader.read()` returning `null` triggers `InterestProcessor.flush()` | |
| APPL-RESULT / APPL-AOK / APPL-EOF | 133-135 | S9(9) COMP; 88 = 0 / 16 | `Optional.isPresent()` / `null` from reader / `InterestServiceException` | Status plumbing has no direct Java state. |
| PARM-DATE | 178 | X(10) (LINKAGE, via `PROCEDURE DIVISION USING EXTERNAL-PARMS`, 180) | job parameter `InterestJobConfig.PARAM_RUN_DATE = "runDate"`, passed as `runDate` to `TransactionFactory.interestTransaction` | JCL `app/jcl/INTCALC.jcl` line 22 `PARM='2022071800'` (YYYYMMDDHH, 10 chars). Value is used verbatim as the TRAN-ID prefix; it is *not* parsed as a date. PARM-LENGTH (177) is never checked. |
| COBOL-TS / DB2-FORMAT-TS | 141-165 | X(26) with REDEFINES | `Db2TimestampSupplier.get()` -> `String` (26 chars) | Built by Z-GET-DB2-FORMAT-TIMESTAMP (613-626). |
| IO-STATUS / IO-STATUS-04 / TWO-BYTES-* | 122-131 | X(2) / 9(4) | message text in `InterestServiceException` | Only used by 9910-DISPLAY-IO-STATUS. |
| ABCODE / TIMING | 138-139 | S9(9) BINARY | none (`InterestServiceException`, exit code non-zero) | 999 / 0 at 630-631. |

## 4. Tests -> paragraphs

| Test class / method | Validates (paragraph / copybook, lines) |
|---|---|
| `calc.InterestCalculatorTest.paragraph1300ComputeInterest_truncatesNotRounds` | 1300-COMPUTE-INTEREST 464-465: `(bal * rate) / 1200` truncated to scale 2 (`RoundingMode.DOWN`), e.g. 1000.00 * 12.34 / 1200 = 10.2833.. -> 10.28; negative balances truncate toward zero |
| `calc.InterestCalculatorTest.paragraph1300ComputeInterest_scaleIsAlwaysTwo` | 1300 464-465: result scale 2 even for exact quotients |
| `calc.InterestCalculatorTest.paragraph1300ComputeInterest_zeroBalanceYieldsZero` | 1300 464-465 |
| `calc.InterestCalculatorTest.paragraph1300ComputeInterest_accumulateAddsToRunningTotal` | 1300 467: `ADD WS-MONTHLY-INT TO WS-TOTAL-INT` |
| `domain.TranCatBalanceTest.copybookCVTRA01Y_parsesFixedWidthRecord` / `_roundTripsToRecord` / `_decodesOverpunchSign` | CVTRA01Y 4-10 against `app/data/ASCII/tcatbal.txt` |
| `domain.DisclosureGroupTest.copybookCVTRA02Y_parsesFixedWidthRecord` / `_roundTripsToRecord` / `_defaultGroupIdIsSpacePadded` | CVTRA02Y 4-10 against `app/data/ASCII/discgrp.txt` |
| `domain.AccountTest.copybookCVACT01Y_parsesFixedWidthRecord` / `_roundTripsToRecord` / `_withBalancesOnlyChangesBalanceFields` | CVACT01Y 4-17 against `app/data/ASCII/acctdata.txt`; 1050 352-354 field subset |
| `domain.CardXrefTest.copybookCVACT03Y_parsesFixedWidthRecord` / `_roundTripsToRecord` | CVACT03Y 4-8 against `app/data/ASCII/cardxref.txt` |
| `domain.TransactionTest.copybookCVTRA05Y_formatsFixedWidthRecord` / `_roundTripsToRecord` / `_encodesNegativeAmountOverpunch` | CVTRA05Y 4-18 (350 bytes) |
| `service.InterestRateServiceTest.paragraph1200GetInterestRate_returnsGroupRate` | 1200 415-435: exact key hit |
| `service.InterestRateServiceTest.paragraph1200GetInterestRate_fallsBackToDefaultOnStatus23` | 1200 436-439 + 1200-A 443-460: missing group -> `DEFAULT` with same type/cat |
| `service.InterestRateServiceTest.paragraph1200AGetDefaultIntRate_abendsWhenDefaultMissing` | 1200-A 446-458: neither key present -> `InterestServiceException` |
| `service.TransactionFactoryTest.paragraph1300BWriteTx_buildsTranIdFromParmDateAndSuffix` | 1300-B 474-480: `2022071800` + `%06d` |
| `service.TransactionFactoryTest.paragraph1300BWriteTx_setsInterestConstants` | 1300-B 482-484, 491-494: `01`, `0005`, `System`, merchant fields |
| `service.TransactionFactoryTest.paragraph1300BWriteTx_descriptionIsPrefixPlusAcctId` | 1300-B 485-489: `Int. for a/c ` + 11-digit id, padded to 100 |
| `service.TransactionFactoryTest.paragraph1300BWriteTx_copiesCardNumAndTimestamps` | 1300-B 495-498: `origTs == procTs` |
| `service.AccountPostingServiceTest.paragraph1050UpdateAccount_addsTotalInterestToCurrBal` | 1050 352 |
| `service.AccountPostingServiceTest.paragraph1050UpdateAccount_zeroesCycleCreditAndDebit` | 1050 353-354 |
| `service.AccountPostingServiceTest.paragraph1050UpdateAccount_zeroTotalStillResetsCycleFields` | 1050 + main loop 214-217: all-zero-rate account rewritten with unchanged balance and zeroed cycle fields |
| `service.AccountPostingServiceTest.paragraph1400ComputeFees_isNoOp` | 1400 518-520 |
| `batch.InterestProcessorTest.mainLoop_controlBreakPostsPreviousAccount` | 188-222, 194-206: two accounts -> first account update emitted when the second account's first record arrives |
| `batch.InterestProcessorTest.mainLoop_firstTimeFlagSuppressesPostingOnFirstBreak` | 195-199: no `accountUpdates` for the very first record |
| `batch.InterestProcessorTest.mainLoop_resetsTotalInterestPerAccount` | 200 |
| `batch.InterestProcessorTest.mainLoop_skipsComputeWhenRateIsZero` | 214-217: no transaction, total unchanged |
| `batch.InterestProcessorTest.mainLoop_tranIdSuffixIsGlobalAcrossAccounts` | 474: suffix continues across control breaks |
| `batch.InterestProcessorTest.mainLoop_flushPostsLastAccountAtEof` | 219-221 |
| `batch.InterestProcessorTest.paragraph1100GetAcctData_missingAccountAbends` | 1100 372-391 -> `InterestServiceException` |
| `batch.InterestProcessorTest.paragraph1110GetXrefData_missingXrefAbends` | 1110 393-413 -> `InterestServiceException` |
| `batch.InterestJobIntegrationTest.procedureDivision_mainLoop_oneTransactionPerNonZeroRateCategoryAndAccountsRewritten` | 180-232 over `app/data/ASCII` with `runDate=2022071800`, both `postFinalAccount` settings; asserts `COMPLETED`, one transaction per non-zero-rate record, sequential TRAN-IDs, posted balances / zeroed cycle fields |
| `batch.GoldenFileParityTest.cbact04c_outputMatchesCobolGoldenFiles` | 180-232 for scenarios `sample-data`, `nonzero-balances`, `single-account`: byte-for-byte compare of `expected/systran.txt` (bytes 279-330 timestamp format only) and `expected/acctdata.txt` produced by CBACT04C under GnuCOBOL |
| `InterestServiceApplicationTests.contextLoads` | Spring wiring of `InterestJobConfig`, repositories, services (already present) |

Fixture layout: `src/test/resources/fixtures/<scenario>/{input,expected}` (see `fixtures/README.md`).

## 5. Semantics notes (cross-cutting)

- **COMPUTE truncation.** `COMPUTE` without `ROUNDED` (464-465) truncates the final result to the
  receiving PIC's scale. Java: `balance.multiply(rate).divide(InterestCalculator.MONTHLY_DIVISOR,
  InterestCalculator.SCALE, RoundingMode.DOWN)`. Truncation is toward zero for negatives, which is what
  `RoundingMode.DOWN` does (`HALF_UP`/`FLOOR` would diverge on e.g. -0.005 or negative balances).
- **ADD size-error truncation.** Neither `ADD` (352, 467, 474) has `ON SIZE ERROR`. On overflow COBOL
  keeps the low-order digits of the result (high-order truncation) and continues. `BigDecimal` never
  overflows, so the Java implementation must decide between reproducing the wrap (e.g. `remainder`
  of 10^n) or throwing. Recommendation: throw `InterestServiceException` - the wrap is a latent data
  corruption in the COBOL, not a business rule, and it is unreachable with the demo data.
- **TRAN-DESC padding.** `STRING ... INTO` writes only the concatenated characters (24 bytes) and leaves
  bytes 25-100 of TRAN-DESC untouched. Java writes spaces; parity test verifies against the golden file.
- **WS-TRANID-SUFFIX formatting.** Unsigned `9(06)` is moved into an alphanumeric `STRING` receiving
  field as six ASCII/EBCDIC digits with leading zeros -> `String.format("%06d", suffix)`. Overflow past
  999999 wraps to 000000 in COBOL (and would then produce duplicate TRAN-IDs).
- **First-time flag.** `WS-FIRST-TIME` starts `'Y'`; the first control break flips it to `'N'` without
  posting. From then on every control break posts the previous account. The EOF branch (220) that
  would post the final account is unreachable (see section 6 item 4).
- **Accounts whose rates are all zero.** The guard at 214 only skips 1300/1400. The control break and
  EOF still run 1050-UPDATE-ACCOUNT, so the account is REWRITTEN with `ACCT-CURR-BAL + 0` and
  `ACCT-CURR-CYC-CREDIT = ACCT-CURR-CYC-DEBIT = 0`, and no transaction is produced. Java must emit the
  `Account` in `InterestItemResult.accountUpdates` even when `transactions` is empty.
- **Rewrite scope.** 1050 rewrites the whole 300-byte record from `ACCOUNT-RECORD`; all non-balance
  fields are those read by 1100 -> `Account.withBalances` preserves them.
- **Timestamps.** `Z-GET-DB2-FORMAT-TIMESTAMP` yields wall-clock time at write; both TRAN-ORIG-TS and
  TRAN-PROC-TS get the same value. Java injects `Db2TimestampSupplier` so runs are reproducible.

## 6. Behavioural differences / open questions

1. **VSAM file status codes.** COBOL branches on 2-byte statuses ('00', '10', '23', '9x'). Java
   repositories return `Optional`, readers return `null` at EOF, and I/O problems surface as
   `IOException`/`InterestServiceException`. 9910-DISPLAY-IO-STATUS's 4-digit rendering (635-648) has
   no runtime equivalent; the exception message should include the COBOL DISPLAY text so operators can
   correlate ('ERROR READING ACCOUNT FILE', etc.).
2. **Abend code.** 9999-ABEND-PROGRAM calls `CEE3ABD` with ABCODE 999 (U0999). Java maps to a
   non-zero process exit via `SpringApplication.exit`; the numeric 999 is not preserved unless an
   `ExitCodeGenerator` is added. Open question: does the scheduler (`app/scheduler/`) key on U0999?
3. **DISPLAY statements.** `DISPLAY TRAN-CAT-BAL-RECORD` (193) prints every input record to SYSOUT;
   start/end banners (181, 230); 'ACCOUNT NOT FOUND' (375, 397); 'DISCLOSURE GROUP RECORD MISSING' /
   'TRY WITH DEFAULT GROUP CODE' (418-419). Java: SLF4J logging (DEBUG for 193, INFO for banners, WARN
   for the fallback). SYSOUT parity is out of scope for the golden-file tests.
4. **Final account is never posted (EOF branch is dead code).** The `ELSE PERFORM 1050-UPDATE-ACCOUNT`
   at 219-221 sits inside `PERFORM UNTIL END-OF-FILE = 'Y'`; once 1000-TCATBALF-GET-NEXT sets
   `END-OF-FILE = 'Y'` the loop terminates before the ELSE is evaluated. The last control group's
   transactions are written (1300-B) but its `ACCT-CURR-BAL` is left unchanged - verified by running the
   program under GnuCOBOL (`fixtures/single-account`, account 7 of `fixtures/nonzero-balances`).
   Java reproduces this by default; job parameter `postFinalAccount=true` performs the evidently
   intended final 1050 via `InterestProcessor.flush()` (which posts nothing on an empty input file).
5. **INVALID KEY on 1100/1110 is effectively fatal.** The `INVALID KEY` clauses only DISPLAY; the
   subsequent status check abends. Java throws `InterestServiceException` directly - same outcome,
   fewer log lines.
6. **Status '23' handling in 1200 vs 1200-A.** 1200 tolerates '23' and falls back; 1200-A does not
   (no `INVALID KEY`, any non-'00' abends). Java `getRate` mirrors: second miss throws.
7. **Rate lookup uses `ACCT-GROUP-ID` of the current account (210)** but this is only refreshed at
   the control break; since TCATBAL is keyed by account id first, all records of an account are
   contiguous, so this is safe. Java relies on the same sorted-input assumption (the reader must
   preserve KSDS key order; `app/data/ASCII/tcatbal.txt` is already sorted).
8. **Duplicate xrefs.** The AIX read returns the first record for the account; multiple cards per
   account are possible in `cardxref.txt`. `InMemoryCardXrefRepository.load` uses `putIfAbsent`, i.e.
   first in file order - equivalent only if file order equals AIX order (card number ascending).
   Parity test should cover an account with two cards.
9. **Arithmetic overflow** (see section 5): COBOL silently wraps; Java recommendation is to throw.
10. **Uninitialised WORKING-STORAGE.** WS-MONTHLY-INT, WS-TOTAL-INT and the non-MOVEd bytes of
    TRAN-RECORD (TRAN-DESC tail, FILLER) have no VALUE clause; their initial content depends on the
    compiler's WORKING-STORAGE initialisation. Java uses 0 / spaces; confirm with the golden file.
11. **`OPEN OUTPUT TRANSACT-FILE`** (309) truncates any existing dataset; the JCL allocates a new
    GDG generation. Java writer should overwrite `PARAM_TRAN_FILE`.
12. **Scaffold `@Trace` line ranges to correct.** The stubs on the base branch cite copybook lines
    `16-30` (Account), `16-20` (CardXref), `16-22` (DisclosureGroup, TranCatBalance), `16-31`
    (Transaction); the actual copybooks have the record at lines 4-17, 4-8, 4-10, 4-10, 4-18
    respectively. `InterestServiceException` cites `631-641`; 9999-ABEND-PROGRAM is 628-632.
    `TransactionFactory` cites `473-500` (fine: the MOVEs; the WRITE/status check is 500-515).
    `AccountPostingService.postInterest` cites `350-356` (fine: REWRITE itself is at 356, the status
    check 357-369 maps to the writer/exception). Owners of those files should update the annotations.
13. **1400-COMPUTE-FEES** is an empty paragraph in COBOL. `computeFees` is a no-op; any future fee
    logic is new behaviour, not a port.
