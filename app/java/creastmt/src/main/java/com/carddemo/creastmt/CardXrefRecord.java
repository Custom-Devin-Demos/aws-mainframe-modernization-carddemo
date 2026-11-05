package com.carddemo.creastmt;

/** CVACT03Y CARD-XREF-RECORD (RECLN 50). */
public record CardXrefRecord(String cardNum, long custId, long acctId) {

    public static final int LENGTH = 50;

    public static CardXrefRecord parse(String line) {
        String r = Cobol.pic(line, LENGTH);
        return new CardXrefRecord(
                Cobol.field(r, 0, 16),
                Cobol.unsigned(Cobol.field(r, 16, 9)),
                Cobol.unsigned(Cobol.field(r, 25, 11)));
    }
}
