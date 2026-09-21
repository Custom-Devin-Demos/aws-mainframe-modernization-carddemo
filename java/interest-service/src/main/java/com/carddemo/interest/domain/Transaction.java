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
@Trace(copybook = "CVTRA05Y", lines = "16-31")
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

    public static Transaction fromRecord(String record) {
        throw new UnsupportedOperationException("Child A: implement CVTRA05Y parsing");
    }

    public String toRecord() {
        throw new UnsupportedOperationException("Child A: implement CVTRA05Y formatting");
    }
}
