package com.carddemo.batch.common.record;

import static com.carddemo.batch.common.record.FieldSpec.alnum;
import static com.carddemo.batch.common.record.FieldSpec.code;
import static com.carddemo.batch.common.record.FieldSpec.filler;
import static com.carddemo.batch.common.record.FieldSpec.signedDecimal;

import java.util.List;

/** Record layouts transcribed 1:1 from the copybooks in {@code app/cpy}. */
public final class Copybooks {

    private Copybooks() {
    }

    /** CVACT01Y ACCOUNT-RECORD, 300 bytes (ACCTDATA KSDS, key ACCT-ID). */
    public static final RecordLayout ACCOUNT_RECORD = new RecordLayout("ACCOUNT-RECORD", "CVACT01Y", List.of(
            code("ACCT-ID", 11),
            alnum("ACCT-ACTIVE-STATUS", 1),
            signedDecimal("ACCT-CURR-BAL", 10, 2),
            signedDecimal("ACCT-CREDIT-LIMIT", 10, 2),
            signedDecimal("ACCT-CASH-CREDIT-LIMIT", 10, 2),
            alnum("ACCT-OPEN-DATE", 10),
            alnum("ACCT-EXPIRAION-DATE", 10),
            alnum("ACCT-REISSUE-DATE", 10),
            signedDecimal("ACCT-CURR-CYC-CREDIT", 10, 2),
            signedDecimal("ACCT-CURR-CYC-DEBIT", 10, 2),
            alnum("ACCT-ADDR-ZIP", 10),
            alnum("ACCT-GROUP-ID", 10),
            filler(178)));

    /** CVACT02Y CARD-RECORD, 150 bytes (CARDDATA KSDS, key CARD-NUM). */
    public static final RecordLayout CARD_RECORD = new RecordLayout("CARD-RECORD", "CVACT02Y", List.of(
            alnum("CARD-NUM", 16),
            code("CARD-ACCT-ID", 11),
            code("CARD-CVV-CD", 3),
            alnum("CARD-EMBOSSED-NAME", 50),
            alnum("CARD-EXPIRAION-DATE", 10),
            alnum("CARD-ACTIVE-STATUS", 1),
            filler(59)));

    /** CVACT03Y CARD-XREF-RECORD, 50 bytes (CARDXREF KSDS, key XREF-CARD-NUM). */
    public static final RecordLayout CARD_XREF_RECORD = new RecordLayout("CARD-XREF-RECORD", "CVACT03Y", List.of(
            alnum("XREF-CARD-NUM", 16),
            code("XREF-CUST-ID", 9),
            code("XREF-ACCT-ID", 11),
            filler(14)));

    /** CVCUS01Y CUSTOMER-RECORD, 500 bytes (CUSTDATA KSDS, key CUST-ID). */
    public static final RecordLayout CUSTOMER_RECORD = new RecordLayout("CUSTOMER-RECORD", "CVCUS01Y", List.of(
            code("CUST-ID", 9),
            alnum("CUST-FIRST-NAME", 25),
            alnum("CUST-MIDDLE-NAME", 25),
            alnum("CUST-LAST-NAME", 25),
            alnum("CUST-ADDR-LINE-1", 50),
            alnum("CUST-ADDR-LINE-2", 50),
            alnum("CUST-ADDR-LINE-3", 50),
            alnum("CUST-ADDR-STATE-CD", 2),
            alnum("CUST-ADDR-COUNTRY-CD", 3),
            alnum("CUST-ADDR-ZIP", 10),
            alnum("CUST-PHONE-NUM-1", 15),
            alnum("CUST-PHONE-NUM-2", 15),
            code("CUST-SSN", 9),
            alnum("CUST-GOVT-ISSUED-ID", 20),
            alnum("CUST-DOB-YYYY-MM-DD", 10),
            alnum("CUST-EFT-ACCOUNT-ID", 10),
            alnum("CUST-PRI-CARD-HOLDER-IND", 1),
            code("CUST-FICO-CREDIT-SCORE", 3),
            filler(168)));

    /** CVTRA01Y TRAN-CAT-BAL-RECORD, 50 bytes (TCATBALF KSDS, key TRANCAT-ACCT-ID+TRANCAT-TYPE-CD+TRANCAT-CD). */
    public static final RecordLayout TRAN_CAT_BAL_RECORD = new RecordLayout("TRAN-CAT-BAL-RECORD", "CVTRA01Y", List.of(
            code("TRANCAT-ACCT-ID", 11),
            alnum("TRANCAT-TYPE-CD", 2),
            code("TRANCAT-CD", 4),
            signedDecimal("TRAN-CAT-BAL", 9, 2),
            filler(22)));

    /** CVTRA03Y TRAN-TYPE-RECORD, 60 bytes (TRANTYPE KSDS, key TRAN-TYPE). */
    public static final RecordLayout TRAN_TYPE_RECORD = new RecordLayout("TRAN-TYPE-RECORD", "CVTRA03Y", List.of(
            alnum("TRAN-TYPE", 2),
            alnum("TRAN-TYPE-DESC", 50),
            filler(8)));

    /** CVTRA04Y TRAN-CAT-RECORD, 60 bytes (TRANCATG KSDS, key TRAN-TYPE-CD+TRAN-CAT-CD). */
    public static final RecordLayout TRAN_CAT_RECORD = new RecordLayout("TRAN-CAT-RECORD", "CVTRA04Y", List.of(
            alnum("TRAN-TYPE-CD", 2),
            code("TRAN-CAT-CD", 4),
            alnum("TRAN-CAT-TYPE-DESC", 50),
            filler(4)));

    /** CVTRA05Y TRAN-RECORD, 350 bytes (TRANSACT KSDS, key TRAN-ID). */
    public static final RecordLayout TRAN_RECORD = new RecordLayout("TRAN-RECORD", "CVTRA05Y", List.of(
            alnum("TRAN-ID", 16),
            alnum("TRAN-TYPE-CD", 2),
            code("TRAN-CAT-CD", 4),
            alnum("TRAN-SOURCE", 10),
            alnum("TRAN-DESC", 100),
            signedDecimal("TRAN-AMT", 9, 2),
            code("TRAN-MERCHANT-ID", 9),
            alnum("TRAN-MERCHANT-NAME", 50),
            alnum("TRAN-MERCHANT-CITY", 50),
            alnum("TRAN-MERCHANT-ZIP", 10),
            alnum("TRAN-CARD-NUM", 16),
            alnum("TRAN-ORIG-TS", 26),
            alnum("TRAN-PROC-TS", 26),
            filler(20)));

    /** CVTRA06Y DALYTRAN-RECORD, 350 bytes (DALYTRAN sequential file). */
    public static final RecordLayout DALYTRAN_RECORD = new RecordLayout("DALYTRAN-RECORD", "CVTRA06Y", List.of(
            alnum("DALYTRAN-ID", 16),
            alnum("DALYTRAN-TYPE-CD", 2),
            code("DALYTRAN-CAT-CD", 4),
            alnum("DALYTRAN-SOURCE", 10),
            alnum("DALYTRAN-DESC", 100),
            signedDecimal("DALYTRAN-AMT", 9, 2),
            code("DALYTRAN-MERCHANT-ID", 9),
            alnum("DALYTRAN-MERCHANT-NAME", 50),
            alnum("DALYTRAN-MERCHANT-CITY", 50),
            alnum("DALYTRAN-MERCHANT-ZIP", 10),
            alnum("DALYTRAN-CARD-NUM", 16),
            alnum("DALYTRAN-ORIG-TS", 26),
            alnum("DALYTRAN-PROC-TS", 26),
            filler(20)));
}
