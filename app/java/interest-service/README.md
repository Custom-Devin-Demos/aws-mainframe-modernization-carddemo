# interest-service

Java / Spring Boot reimplementation of the COBOL batch program
[`app/cbl/CBACT04C.cbl`](../../cbl/CBACT04C.cbl) — the CardDemo monthly **interest
calculator**.

## What CBACT04C does

1. Reads transaction category balances (`TCATBALF`) sequentially, ordered by account.
2. On each account break, looks up the account record (`ACCTFILE`) and the card
   cross-reference (`XREFFILE`).
3. For each category balance, resolves the interest rate from the disclosure group
   file (`DISCGRP`), falling back to the `DEFAULT` group when the account's group is
   missing.
4. Computes monthly interest: `(categoryBalance * annualRatePercent) / 1200`
   (COBOL `1300-COMPUTE-INTEREST`).
5. Writes an interest transaction per non-zero rate (type `01`, category `05`,
   source `System`).
6. Adds the accumulated interest to the account balance and resets the current-cycle
   credit/debit totals (COBOL `1050-UPDATE-ACCOUNT`).

`1400-COMPUTE-FEES` is not implemented in the COBOL source and remains a stub here.

## Monetary precision

All monetary and rate values use `BigDecimal`. To match COBOL `PIC ...V99`
truncation, monetary results are scaled to 2 decimals with `RoundingMode.DOWN`.

## Build & test

This module uses the Maven Wrapper, so no local Maven install is required (only a
JDK 21+):

```bash
cd app/java/interest-service
./mvnw test          # run unit + integration tests (H2)
./mvnw package       # build the jar
```

## Layout

| Package                              | Contents                                            |
|--------------------------------------|-----------------------------------------------------|
| `com.carddemo.interest.entity`       | JPA entities mapping the COBOL copybooks            |
| `com.carddemo.interest.repository`   | Spring Data repositories                            |
| `com.carddemo.interest.service`      | `InterestCalculator`, `InterestRateResolver`, `InterestCalculationService` |
| `com.carddemo.interest.batch`        | Spring Batch job/partitioner (replaces `INTCALC.jcl`) |
