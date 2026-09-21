package com.carddemo.interest.domain;

import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;

/**
 * ACCOUNT-RECORD (copybook CVACT01Y, RECLN 300).
 *
 * <pre>
 * 05 ACCT-ID                 PIC 9(11)
 * 05 ACCT-ACTIVE-STATUS      PIC X(01)
 * 05 ACCT-CURR-BAL           PIC S9(10)V99
 * 05 ACCT-CREDIT-LIMIT       PIC S9(10)V99
 * 05 ACCT-CASH-CREDIT-LIMIT  PIC S9(10)V99
 * 05 ACCT-OPEN-DATE          PIC X(10)
 * 05 ACCT-EXPIRAION-DATE     PIC X(10)
 * 05 ACCT-REISSUE-DATE       PIC X(10)
 * 05 ACCT-CURR-CYC-CREDIT    PIC S9(10)V99
 * 05 ACCT-CURR-CYC-DEBIT     PIC S9(10)V99
 * 05 ACCT-ADDR-ZIP           PIC X(10)
 * 05 ACCT-GROUP-ID           PIC X(10)
 * 05 FILLER                  PIC X(178)
 * </pre>
 *
 * All S9(10)V99 fields are {@link BigDecimal} with scale 2.
 */
@Trace(copybook = "CVACT01Y", lines = "16-30")
public record Account(
        String id,
        String activeStatus,
        BigDecimal currBal,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        String openDate,
        String expirationDate,
        String reissueDate,
        BigDecimal currCycCredit,
        BigDecimal currCycDebit,
        String addrZip,
        String groupId) {

    public static final int RECORD_LENGTH = 300;

    public static Account fromRecord(String record) {
        throw new UnsupportedOperationException("Child A: implement CVACT01Y parsing");
    }

    public String toRecord() {
        throw new UnsupportedOperationException("Child A: implement CVACT01Y formatting");
    }

    /** Copy with new balance / cycle fields (used by AccountPostingService.postInterest). */
    public Account withBalances(BigDecimal currBal, BigDecimal currCycCredit, BigDecimal currCycDebit) {
        return new Account(id, activeStatus, currBal, creditLimit, cashCreditLimit, openDate,
                expirationDate, reissueDate, currCycCredit, currCycDebit, addrZip, groupId);
    }
}
