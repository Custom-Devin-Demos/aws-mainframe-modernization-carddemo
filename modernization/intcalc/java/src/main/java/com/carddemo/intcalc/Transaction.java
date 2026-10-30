package com.carddemo.intcalc;

import java.math.BigDecimal;

/** TRAN-RECORD (copybook CVTRA05Y, 350 bytes) as written to the TRANSACT (SYSTRAN) file. */
public record Transaction(
        String tranId,
        String typeCode,
        String catCode,
        String source,
        String description,
        BigDecimal amount,
        long merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String cardNum,
        String origTimestamp,
        String procTimestamp) {

    public static final int LENGTH = 350;

    public String toRecord() {
        String r = FixedWidth.alpha(tranId, 16)
                + FixedWidth.alpha(typeCode, 2)
                + CobolNumeric.formatUnsigned(Long.parseLong(catCode), 4)
                + FixedWidth.alpha(source, 10)
                + FixedWidth.alpha(description, 100)
                + CobolNumeric.formatSigned(amount, 9, 2)
                + CobolNumeric.formatUnsigned(merchantId, 9)
                + FixedWidth.alpha(merchantName, 50)
                + FixedWidth.alpha(merchantCity, 50)
                + FixedWidth.alpha(merchantZip, 10)
                + FixedWidth.alpha(cardNum, 16)
                + FixedWidth.alpha(origTimestamp, 26)
                + FixedWidth.alpha(procTimestamp, 26)
                + " ".repeat(20);
        if (r.length() != LENGTH) {
            throw new IllegalStateException("TRAN-RECORD length " + r.length());
        }
        return r;
    }
}
