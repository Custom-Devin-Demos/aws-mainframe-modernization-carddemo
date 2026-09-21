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
@Trace(copybook = "CVTRA01Y", lines = "16-22")
public record TranCatBalance(String acctId, String typeCd, String catCd, BigDecimal balance) {

    public static final int RECORD_LENGTH = 50;

    /** Parse a fixed-width 50-byte record (ASCII, zoned-decimal sign overpunch on the balance). */
    public static TranCatBalance fromRecord(String record) {
        throw new UnsupportedOperationException("Child A: implement CVTRA01Y parsing");
    }

    /** Format back to the 50-byte fixed-width layout. */
    public String toRecord() {
        throw new UnsupportedOperationException("Child A: implement CVTRA01Y formatting");
    }
}
