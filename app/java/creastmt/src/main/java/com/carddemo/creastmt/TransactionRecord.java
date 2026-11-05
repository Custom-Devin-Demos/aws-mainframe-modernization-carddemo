package com.carddemo.creastmt;

import java.math.BigDecimal;

/**
 * COSTM01 TRNX-RECORD (RECLN 350): the TRANSACT record re-keyed by card number + transaction id,
 * as produced by the CREASTMT SORT/OUTREC step. TRNX-AMT is PIC S9(09)V99.
 */
public record TransactionRecord(
        String cardNum,
        String id,
        String typeCd,
        int catCd,
        String source,
        String desc,
        BigDecimal amt,
        long merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String origTs,
        String procTs) {

    public static final int LENGTH = 350;

    public static TransactionRecord parse(String line) {
        String r = Cobol.pic(line, LENGTH);
        return new TransactionRecord(
                Cobol.field(r, 0, 16),
                Cobol.field(r, 16, 16),
                Cobol.field(r, 32, 2),
                (int) Cobol.unsigned(Cobol.field(r, 34, 4)),
                Cobol.field(r, 38, 10),
                Cobol.field(r, 48, 100),
                Cobol.zoned(Cobol.field(r, 148, 11), 2),
                Cobol.unsigned(Cobol.field(r, 159, 9)),
                Cobol.field(r, 168, 50),
                Cobol.field(r, 218, 50),
                Cobol.field(r, 268, 10),
                Cobol.field(r, 278, 26),
                Cobol.field(r, 304, 26));
    }
}
