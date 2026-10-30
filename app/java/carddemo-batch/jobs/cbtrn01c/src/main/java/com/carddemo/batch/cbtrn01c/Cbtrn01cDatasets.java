package com.carddemo.batch.cbtrn01c;

import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.record.RecordLayout;
import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.ColumnType;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.util.List;
import java.util.Map;

/** Output dataset contracts of CBTRN01C (design §5). */
final class Cbtrn01cDatasets {

    static final String DAILY_TRAN_VERIFICATION = "daily_tran_verification";

    static final String VERIFIED = "VERIFIED";
    static final String CARD_NOT_VERIFIED = "CARD_NOT_VERIFIED";
    static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

    static final DatasetSchema DAILY_TRAN_VERIFICATION_SCHEMA = new DatasetSchema(DAILY_TRAN_VERIFICATION,
            "CBTRN01C: one row per XREF lookup (each DALYTRAN record, plus the EOF re-lookup of the last record)",
            List.of(
                    Column.of("lookup_seq", ColumnType.LONG, "1-based lookup number in processing order"),
                    Column.nullable("dalytran_record_no", ColumnType.LONG,
                            "1-based DALYTRAN record whose data was looked up; null when DALYTRAN was empty"),
                    Column.of("eof_reprocessed", ColumnType.BOOLEAN,
                            "true for the lookup MAIN-PARA repeats after end of file on the last DALYTRAN-RECORD"),
                    fromCopybook(Copybooks.DALYTRAN_RECORD, "DALYTRAN-ID", "dalytran_id", false),
                    fromCopybook(Copybooks.DALYTRAN_RECORD, "DALYTRAN-CARD-NUM", "dalytran_card_num", false),
                    Column.of("xref_read_status", ColumnType.INT, "WS-XREF-READ-STATUS: 0 found, 4 INVALID KEY"),
                    fromCopybook(Copybooks.CARD_XREF_RECORD, "XREF-ACCT-ID", "xref_acct_id", true),
                    fromCopybook(Copybooks.CARD_XREF_RECORD, "XREF-CUST-ID", "xref_cust_id", true),
                    Column.nullable("acct_read_status", ColumnType.INT,
                            "WS-ACCT-READ-STATUS: 0 found, 4 INVALID KEY; null when the account was not read"),
                    Column.of("verification_status", ColumnType.STRING,
                            "VERIFIED | CARD_NOT_VERIFIED | ACCOUNT_NOT_FOUND")));

    private Cbtrn01cDatasets() {
    }

    private static Column fromCopybook(RecordLayout layout, String cobolField, String name, boolean nullable) {
        String source = layout.field(cobolField).columnName();
        Map<String, Object> md = DatasetSchema.fromLayout("x", "x", layout).column(source).metadata();
        return new Column(name, ColumnType.STRING, nullable, cobolField, md);
    }
}
