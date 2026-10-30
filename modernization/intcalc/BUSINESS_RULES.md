# INTCALC / CBACT04C business rules

Source: `app/jcl/INTCALC.jcl` (STEP15, `PARM='2022071800'`) and `app/cbl/CBACT04C.cbl`.
Java: `java/src/main/java/com/carddemo/intcalc/`. Each rule lists the COBOL paragraph it comes from, the Java code that implements it, and the tests that cover it.

## Inputs and outputs

| DD (JCL) | Dataset | Copybook | Access in COBOL | Java input / output |
|---|---|---|---|---|
| TCATBALF | TCATBALF KSDS, key acct(11)+type(2)+cat(4) | CVTRA01Y (50 B) | sequential, key order | `tcatbal.txt` |
| XREFFILE / XREFFIL1 | CARDXREF KSDS + AIX on acct id | CVACT03Y (50 B) | random by alternate key | `cardxref.txt` |
| ACCTFILE | ACCTDATA KSDS, key acct id | CVACT01Y (300 B) | random read + REWRITE | `acctdata.txt` in, `acctdata.dat` out |
| DISCGRP | DISCGRP KSDS, key group(10)+type(2)+cat(4) | CVTRA02Y (50 B) | random | `discgrp.txt` |
| TRANSACT | SYSTRAN(+1) GDG, RECFM=F LRECL=350 | CVTRA05Y (350 B) | sequential write | `transact.dat` out |

Java reads line-sequential ASCII extracts (the format already in `app/data/ASCII`) and writes fixed-length records with no delimiters, like `RECFM=F`.

## Rules

| # | Rule | COBOL | Java | Tests |
|---|---|---|---|---|
| R1 | Category balances are processed in TCATBALF key order: account id, then tran type code, then tran category code. A change in `TRANCAT-ACCT-ID` is a control break. | main loop, `1000-TCATBALF-GET-NEXT` | `InterestCalculationJob.run` (sorts by `TranCatBalance.key()`) | `InterestCalculationJobTest.processesBalancesInKsdsKeyOrder` |
| R2 | On each control break, read the account (`ACCTFILE`) and card xref (by account id). A missing account or xref abends. | `1100-GET-ACCT-DATA`, `1110-GET-XREF-DATA` | `InterestCalculationJob.run` | `missingAccountXrefOrDefaultRateAbends` |
| R3 | Look up the interest rate by (`ACCT-GROUP-ID`, `TRANCAT-TYPE-CD`, `TRANCAT-CD`). On status `23` (not found) retry with group `DEFAULT`. A missing DEFAULT row abends. A row that exists with rate 0 is a hit and does not fall back. | `1200-GET-INTEREST-RATE`, `1200-A-GET-DEFAULT-INT-RATE` | `InterestRateTable.rateFor` | `InterestRateTableTest` (all) |
| R4 | Monthly interest = `TRAN-CAT-BAL * DIS-INT-RATE / 1200` into `PIC S9(09)V99`. Scale 2, truncated toward zero (no `ROUNDED`), so `RoundingMode.DOWN`, including negative balances. High-order digits that overflow 9 integer digits are dropped (no `ON SIZE ERROR`). | `1300-COMPUTE-INTEREST` | `InterestCalculator.monthlyInterest`, `CobolNumeric.fitPic` | `InterestCalculatorTest` (all), `CobolNumericTest.fitPic*` |
| R5 | When the rate is 0 the category is skipped: no interest, no transaction, no TRAN-ID used. When the rate is non-zero but interest truncates to 0.00, a 0.00 transaction is still written. | `IF DIS-INT-RATE NOT = 0` | `InterestCalculationJob.run` | `zeroRateCategoriesAreSkipped`, `zeroInterestWithNonZeroRateIsStillWritten` |
| R6 | Interest is summed per account (`WS-TOTAL-INT`, `S9(09)V99`). On the control break the total is added to `ACCT-CURR-BAL`, `ACCT-CURR-CYC-CREDIT` and `ACCT-CURR-CYC-DEBIT` are set to 0, and the record is rewritten. All other bytes stay the same. This happens even when the total is 0. Accounts with no TCATBALF rows are not touched. | `1050-UPDATE-ACCOUNT` | `Account.applyInterest` | `accumulatesInterestAndUpdatesAccountOnControlBreak`, `accountWithOnlyZeroRatesStillHasCycleTotalsReset`, `accountsWithoutBalancesAreUntouched`, `RecordLayoutTest.accountRewriteOnlyTouchesBalanceAndCycleFields` |
| R7 | **CBACT04C does not update the last account in the file.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` end-of-file branch can't be reached: `END-OF-FILE` is set to `'Y'` inside the same pass, so `PERFORM UNTIL` exits first. Its interest transactions are still written. Java copies this by default and fixes it with `--update-last-account`. | main loop | `JobConfig.updateLastAccount` | `lastAccountIsNotRewrittenInParityMode`, `lastAccountIsRewrittenWhenFixEnabled` |
| R8 | One interest transaction per category whose rate is non-zero: `TRAN-ID` = PARM-DATE(10) + 6-digit sequence. The sequence starts at 1, runs across all accounts, and wraps at 999999. Type `01`, category `0005`, source `System`, description `Int. for a/c <ACCT-ID>`, amount = monthly interest, merchant id 0, merchant name/city/zip blank. Orig and proc timestamps use the DB2 format `YYYY-MM-DD-HH.MM.SS.hh0000`. | `1300-B-WRITE-TX`, `Z-GET-DB2-FORMAT-TIMESTAMP` | `InterestCalculationJob.interestTransaction`, `JobConfig.db2Timestamp` | `interestTransactionFields`, `tranIdSequenceSpansAccounts`, `CobolNumericTest.formatsUnsignedKeepingLowOrderDigits` |
| R9 | The card number comes from the xref record for the account. When several cards share an account (duplicate AIX key) the first one is used, which is the lowest card number for an AIX built from the KSDS. | `1110-GET-XREF-DATA` | `InterestCalculationJob.firstCardPerAccount` | `cardNumberIsFirstXrefCardForAccount` |
| R10 | I/O and lookup errors abend the step (`CEE3ABD`, RC 999). | `9999-ABEND-PROGRAM` | `IntCalcException` and exit code 16 from `Main` | `missingAccountXrefOrDefaultRateAbends` |
| R11 | Money is fixed-point: zoned decimal `S9(n)V99` with trailing overpunch. Negative zero is written as positive zero. Java uses `BigDecimal` only, never `double`. | copybooks | `CobolNumeric`, record classes | `CobolNumericTest`, `RecordLayoutTest` |
| R12 | Fees (`1400-COMPUTE-FEES`) are an empty stub in COBOL and are not ported. | `1400-COMPUTE-FEES` | comment in `InterestCalculationJob` | `noFeeTransactionsAreProduced` |

## Known differences from the mainframe run

* **Timestamps:** CBACT04C calls `FUNCTION CURRENT-DATE` for each transaction, so timestamps move forward during a run. The Java job writes one processing timestamp, from `--processing-ts` or the time the job starts, on every record.
* **Abend side effects:** CBACT04C abends partway through the run. By then some accounts are already rewritten and some transactions already written. The Java job validates and computes everything in memory and writes nothing if it fails. That makes a rerun safe, but a failed run doesn't leave the same partial output as the mainframe.
* **Packaging:** The JCL DDs become files in an input and output directory. In the container these are staged from and to S3 (`java/aws/entrypoint.sh`).
