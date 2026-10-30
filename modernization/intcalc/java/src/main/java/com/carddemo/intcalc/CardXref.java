package com.carddemo.intcalc;

/** CARD-XREF-RECORD (copybook CVACT03Y, 50 bytes). */
public record CardXref(String cardNum, String custId, String acctId) {

    public static final int LENGTH = 50;

    public static CardXref parse(String line) {
        String r = FixedWidth.normalise(line, LENGTH);
        return new CardXref(
                FixedWidth.field(r, 0, 16),
                FixedWidth.field(r, 16, 9),
                FixedWidth.field(r, 25, 11));
    }
}
