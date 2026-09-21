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
@Trace(copybook = "CVACT03Y", lines = "16-20")
public record CardXref(String cardNum, String custId, String acctId) {

    public static final int RECORD_LENGTH = 50;

    public static CardXref fromRecord(String record) {
        throw new UnsupportedOperationException("Child A: implement CVACT03Y parsing");
    }

    public String toRecord() {
        throw new UnsupportedOperationException("Child A: implement CVACT03Y formatting");
    }
}
