package com.carddemo.interest.domain;

import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;

/**
 * TRAN-CAT-BAL-RECORD (copybook CVTRA01Y, RECLN 50).
 *
 * <pre>
 * 05 TRAN-CAT-KEY
 *    10 TRANCAT-ACCT-ID   PIC 9(11)
 *    10 TRANCAT-TYPE-CD   PIC X(02)
 *    10 TRANCAT-CD        PIC 9(04)
 * 05 TRAN-CAT-BAL         PIC S9(09)V99
 * 05 FILLER               PIC X(22)
 * </pre>
 *
 * @param acctId  TRANCAT-ACCT-ID, 11 digits (kept as String to preserve leading zeros)
 * @param typeCd  TRANCAT-TYPE-CD, 2 chars
 * @param catCd   TRANCAT-CD, 4 digits
 * @param balance TRAN-CAT-BAL, scale 2
 */
@Trace(copybook = "CVTRA01Y", lines = "4-10")
public record TranCatBalance(String acctId, String typeCd, String catCd, BigDecimal balance) {

    public static final int RECORD_LENGTH = 50;

    public TranCatBalance {
        balance = CobolFields.scale2(balance, "balance");
    }

    /** Parse a fixed-width 50-byte record (ASCII, zoned-decimal sign overpunch on the balance). */
    @Trace(copybook = "CVTRA01Y", lines = "4-10")
    public static TranCatBalance fromRecord(String record) {
        String value = CobolFields.checkLength(record, RECORD_LENGTH);
        return new TranCatBalance(
                CobolFields.pic9(value, 0, 11),
                CobolFields.picX(value, 11, 2),
                CobolFields.pic9(value, 13, 4),
                CobolFields.picS9V99(value, 17, 11));
    }

    /** Format back to the 50-byte fixed-width layout. */
    @Trace(copybook = "CVTRA01Y", lines = "4-10")
    public String toRecord() {
        String result = CobolFields.format9(acctId, 11)
                + CobolFields.formatX(typeCd, 2)
                + CobolFields.format9(catCd, 4)
                + CobolFields.formatS9V99(balance, 11)
                + " ".repeat(22);
        if (result.length() != RECORD_LENGTH) {
            throw new IllegalStateException("record length " + result.length() + ", expected " + RECORD_LENGTH);
        }
        return result;
    }
}
