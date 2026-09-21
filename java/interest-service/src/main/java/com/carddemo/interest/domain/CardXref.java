package com.carddemo.interest.domain;

import com.carddemo.interest.trace.Trace;

/**
 * CARD-XREF-RECORD (copybook CVACT03Y, RECLN 50).
 *
 * <pre>
 * 05 XREF-CARD-NUM  PIC X(16)
 * 05 XREF-CUST-ID   PIC 9(09)
 * 05 XREF-ACCT-ID   PIC 9(11)
 * 05 FILLER         PIC X(14)
 * </pre>
 */
@Trace(copybook = "CVACT03Y", lines = "4-8")
public record CardXref(String cardNum, String custId, String acctId) {

    public static final int RECORD_LENGTH = 50;

    @Trace(copybook = "CVACT03Y", lines = "4-8")
    public static CardXref fromRecord(String record) {
        String value = CobolFields.checkLength(record, RECORD_LENGTH);
        return new CardXref(
                CobolFields.picX(value, 0, 16),
                CobolFields.pic9(value, 16, 9),
                CobolFields.pic9(value, 25, 11));
    }

    @Trace(copybook = "CVACT03Y", lines = "4-8")
    public String toRecord() {
        String result = CobolFields.formatX(cardNum, 16)
                + CobolFields.format9(custId, 9)
                + CobolFields.format9(acctId, 11)
                + " ".repeat(14);
        if (result.length() != RECORD_LENGTH) {
            throw new IllegalStateException("record length " + result.length() + ", expected " + RECORD_LENGTH);
        }
        return result;
    }
}
