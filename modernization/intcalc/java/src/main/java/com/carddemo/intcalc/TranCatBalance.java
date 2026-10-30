package com.carddemo.intcalc;

import java.math.BigDecimal;

/** TRAN-CAT-BAL-RECORD (copybook CVTRA01Y, 50 bytes). */
public record TranCatBalance(String acctId, String typeCode, String catCode, BigDecimal balance) {

    public static final int LENGTH = 50;

    public static TranCatBalance parse(String line) {
        String r = FixedWidth.normalise(line, LENGTH);
        return new TranCatBalance(
                FixedWidth.field(r, 0, 11),
                FixedWidth.field(r, 11, 2),
                FixedWidth.field(r, 13, 4),
                CobolNumeric.parseSigned(FixedWidth.field(r, 17, 11), 2));
    }

    /** TRAN-CAT-KEY: the KSDS key that defines processing order. */
    public String key() {
        return acctId + typeCode + catCode;
    }
}
