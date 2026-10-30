package com.carddemo.intcalc;

import java.math.BigDecimal;

/**
 * ACCOUNT-RECORD (copybook CVACT01Y, 300 bytes). Only the fields CBACT04C reads or rewrites
 * are decoded; every other byte is carried through unchanged, like a COBOL REWRITE.
 */
public final class Account {

    public static final int LENGTH = 300;

    private static final int CURR_BAL = 12;
    private static final int CYC_CREDIT = 78;
    private static final int CYC_DEBIT = 90;
    private static final int GROUP_ID = 112;

    private final StringBuilder record;

    private Account(String record) {
        this.record = new StringBuilder(record);
    }

    public static Account parse(String line) {
        return new Account(FixedWidth.normalise(line, LENGTH));
    }

    public String acctId() {
        return FixedWidth.field(record.toString(), 0, 11);
    }

    public String groupId() {
        return FixedWidth.field(record.toString(), GROUP_ID, 10);
    }

    public BigDecimal currentBalance() {
        return money(CURR_BAL);
    }

    public BigDecimal currentCycleCredit() {
        return money(CYC_CREDIT);
    }

    public BigDecimal currentCycleDebit() {
        return money(CYC_DEBIT);
    }

    /** Paragraph 1050-UPDATE-ACCOUNT. */
    void applyInterest(BigDecimal totalInterest) {
        setMoney(CURR_BAL, currentBalance().add(totalInterest));
        setMoney(CYC_CREDIT, BigDecimal.ZERO);
        setMoney(CYC_DEBIT, BigDecimal.ZERO);
    }

    public String toRecord() {
        return record.toString();
    }

    private BigDecimal money(int offset) {
        return CobolNumeric.parseSigned(FixedWidth.field(record.toString(), offset, 12), 2);
    }

    private void setMoney(int offset, BigDecimal value) {
        record.replace(offset, offset + 12, CobolNumeric.formatSigned(value, 10, 2));
    }
}
