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
@Trace(copybook = "CVACT01Y", lines = "4-17")
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

    public Account {
        currBal = CobolFields.scale2(currBal, "currBal");
        creditLimit = CobolFields.scale2(creditLimit, "creditLimit");
        cashCreditLimit = CobolFields.scale2(cashCreditLimit, "cashCreditLimit");
        currCycCredit = CobolFields.scale2(currCycCredit, "currCycCredit");
        currCycDebit = CobolFields.scale2(currCycDebit, "currCycDebit");
    }

    @Trace(copybook = "CVACT01Y", lines = "4-17")
    public static Account fromRecord(String record) {
        String value = CobolFields.checkLength(record, RECORD_LENGTH);
        return new Account(
                CobolFields.pic9(value, 0, 11),
                CobolFields.picX(value, 11, 1),
                CobolFields.picS9V99(value, 12, 12),
                CobolFields.picS9V99(value, 24, 12),
                CobolFields.picS9V99(value, 36, 12),
                CobolFields.picX(value, 48, 10),
                CobolFields.picX(value, 58, 10),
                CobolFields.picX(value, 68, 10),
                CobolFields.picS9V99(value, 78, 12),
                CobolFields.picS9V99(value, 90, 12),
                CobolFields.picX(value, 102, 10),
                CobolFields.picX(value, 112, 10));
    }

    @Trace(copybook = "CVACT01Y", lines = "4-17")
    public String toRecord() {
        String result = CobolFields.format9(id, 11)
                + CobolFields.formatX(activeStatus, 1)
                + CobolFields.formatS9V99(currBal, 12)
                + CobolFields.formatS9V99(creditLimit, 12)
                + CobolFields.formatS9V99(cashCreditLimit, 12)
                + CobolFields.formatX(openDate, 10)
                + CobolFields.formatX(expirationDate, 10)
                + CobolFields.formatX(reissueDate, 10)
                + CobolFields.formatS9V99(currCycCredit, 12)
                + CobolFields.formatS9V99(currCycDebit, 12)
                + CobolFields.formatX(addrZip, 10)
                + CobolFields.formatX(groupId, 10)
                + " ".repeat(178);
        if (result.length() != RECORD_LENGTH) {
            throw new IllegalStateException("record length " + result.length() + ", expected " + RECORD_LENGTH);
        }
        return result;
    }

    /** Copy with new balance / cycle fields (used by AccountPostingService.postInterest). */
    public Account withBalances(BigDecimal currBal, BigDecimal currCycCredit, BigDecimal currCycDebit) {
        return new Account(id, activeStatus, currBal, creditLimit, cashCreditLimit, openDate,
                expirationDate, reissueDate, currCycCredit, currCycDebit, addrZip, groupId);
    }
}
