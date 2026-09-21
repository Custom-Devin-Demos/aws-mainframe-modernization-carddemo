package com.carddemo.interest.domain;

import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;

/**
 * TRAN-RECORD (copybook CVTRA05Y, RECLN 350).
 *
 * <pre>
 * 05 TRAN-ID             PIC X(16)
 * 05 TRAN-TYPE-CD        PIC X(02)
 * 05 TRAN-CAT-CD         PIC 9(04)
 * 05 TRAN-SOURCE         PIC X(10)
 * 05 TRAN-DESC           PIC X(100)
 * 05 TRAN-AMT            PIC S9(09)V99
 * 05 TRAN-MERCHANT-ID    PIC 9(09)
 * 05 TRAN-MERCHANT-NAME  PIC X(50)
 * 05 TRAN-MERCHANT-CITY  PIC X(50)
 * 05 TRAN-MERCHANT-ZIP   PIC X(10)
 * 05 TRAN-CARD-NUM       PIC X(16)
 * 05 TRAN-ORIG-TS        PIC X(26)
 * 05 TRAN-PROC-TS        PIC X(26)
 * 05 FILLER              PIC X(20)
 * </pre>
 */
@Trace(copybook = "CVTRA05Y", lines = "4-18")
public record Transaction(
        String id,
        String typeCd,
        String catCd,
        String source,
        String description,
        BigDecimal amount,
        String merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String cardNum,
        String origTs,
        String procTs) {

    public static final int RECORD_LENGTH = 350;

    public Transaction {
        amount = CobolFields.scale2(amount, "amount");
    }

    @Trace(copybook = "CVTRA05Y", lines = "4-18")
    public static Transaction fromRecord(String record) {
        String value = CobolFields.checkLength(record, RECORD_LENGTH);
        return new Transaction(
                CobolFields.picX(value, 0, 16),
                CobolFields.picX(value, 16, 2),
                CobolFields.pic9(value, 18, 4),
                CobolFields.picX(value, 22, 10),
                CobolFields.picX(value, 32, 100),
                CobolFields.picS9V99(value, 132, 11),
                CobolFields.pic9(value, 143, 9),
                CobolFields.picX(value, 152, 50),
                CobolFields.picX(value, 202, 50),
                CobolFields.picX(value, 252, 10),
                CobolFields.picX(value, 262, 16),
                CobolFields.picX(value, 278, 26),
                CobolFields.picX(value, 304, 26));
    }

    @Trace(copybook = "CVTRA05Y", lines = "4-18")
    public String toRecord() {
        String result = CobolFields.formatX(id, 16)
                + CobolFields.formatX(typeCd, 2)
                + CobolFields.format9(catCd, 4)
                + CobolFields.formatX(source, 10)
                + CobolFields.formatX(description, 100)
                + CobolFields.formatS9V99(amount, 11)
                + CobolFields.format9(merchantId, 9)
                + CobolFields.formatX(merchantName, 50)
                + CobolFields.formatX(merchantCity, 50)
                + CobolFields.formatX(merchantZip, 10)
                + CobolFields.formatX(cardNum, 16)
                + CobolFields.formatX(origTs, 26)
                + CobolFields.formatX(procTs, 26)
                + " ".repeat(20);
        if (result.length() != RECORD_LENGTH) {
            throw new IllegalStateException("record length " + result.length() + ", expected " + RECORD_LENGTH);
        }
        return result;
    }
}
