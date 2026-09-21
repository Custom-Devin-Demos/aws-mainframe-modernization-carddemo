# COBOL -> Java Traceability Matrix: CBACT04C -> interest-service

Source program: `app/cbl/CBACT04C.cbl` (interest calculator, JCL `app/jcl/INTCALC.jcl` STEP15).
Java module: `java/interest-service`, base package `com.carddemo.interest`.

Every Java element listed here also carries a `@com.carddemo.interest.trace.Trace` annotation with
the same program/copybook, paragraph and line range. Line numbers refer to the files in `app/cbl`
and `app/cpy` on the `main` branch.

## Conventions

- `@Trace(program = "CBACT04C", paragraph = "<PARAGRAPH>", lines = "<from>-<to>")` for procedure logic.
- `@Trace(copybook = "<COPYBOOK>", lines = "<from>-<to>")` for record layouts.
- Test methods are named after the COBOL paragraph they validate, e.g. `paragraph1300ComputeInterest_truncatesNotRounds`.

## 1. Copybooks -> domain records

| Copybook | COBOL structure | Lines | Java type | Notes |
|---|---|---|---|---|
| CVTRA01Y | TRAN-CAT-BAL-RECORD | 4-10 | `domain.TranCatBalance` | |
| CVTRA02Y | DIS-GROUP-RECORD | 4-10 | `domain.DisclosureGroup` | |
| CVACT01Y | ACCOUNT-RECORD | 4-17 | `domain.Account` | |
| CVACT03Y | CARD-XREF-RECORD | 4-8 | `domain.CardXref` | |
| CVTRA05Y | TRAN-RECORD | 4-18 | `domain.Transaction` | |

## 2. Paragraphs -> Java classes / methods

| COBOL paragraph | Lines | Java element | Notes |
|---|---|---|---|
| PROCEDURE DIVISION (main loop) | 188-222 | `batch.InterestProcessor.process` | control break on TRANCAT-ACCT-ID |
| 0000-0400 *-OPEN | 234-323 | | |
| 1000-TCATBALF-GET-NEXT | 325-348 | | |
| 1050-UPDATE-ACCOUNT | 350-370 | `service.AccountPostingService.postInterest` | |
| 1100-GET-ACCT-DATA | 372-391 | | |
| 1110-GET-XREF-DATA | 393-413 | | |
| 1200-GET-INTEREST-RATE | 415-440 | `service.InterestRateService.getRate` | |
| 1200-A-GET-DEFAULT-INT-RATE | 443-460 | `service.InterestRateService.getRate` | |
| 1300-COMPUTE-INTEREST | 462-470 | `calc.InterestCalculator.computeMonthlyInterest` | |
| 1300-B-WRITE-TX | 473-515 | `service.TransactionFactory.interestTransaction` | |
| 1400-COMPUTE-FEES | 518-520 | `service.AccountPostingService.computeFees` | To be implemented |
| 9000-9400 *-CLOSE | 522-... | | |
| 9910-DISPLAY-IO-STATUS | | | |
| 9999-ABEND-PROGRAM | | `service.InterestServiceException` | |
| Z-GET-DB2-FORMAT-TIMESTAMP | | | |

## 3. Working-storage -> Java state

| COBOL item | Lines | Java element | Notes |
|---|---|---|---|
| WS-LAST-ACCT-NUM | 167 | | |
| WS-MONTHLY-INT | 168 | | |
| WS-TOTAL-INT | 169 | | |
| WS-FIRST-TIME | 170 | | |
| WS-RECORD-COUNT | 172 | | |
| WS-TRANID-SUFFIX | 173 | | |
| PARM-DATE | 178 | `batch.InterestJobConfig.PARAM_RUN_DATE` | JCL PARM='2022071800' |

## 4. Tests -> paragraphs

| Test class / method | Validates |
|---|---|
| | |
