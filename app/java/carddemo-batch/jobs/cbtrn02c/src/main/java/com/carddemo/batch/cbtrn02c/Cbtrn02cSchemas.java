package com.carddemo.batch.cbtrn02c;

import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.ColumnType;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Output dataset contracts of CBTRN02C (design doc section 5). */
public final class Cbtrn02cSchemas {

    public static final String POSTED_TRANSACTIONS = "posted_transactions";
    public static final String DAILY_REJECTS = "daily_rejects";
    public static final String ACCOUNT_MASTER = "account_master";
    public static final String TRAN_CAT_BALANCE = "tran_cat_balance";

    public static final String VALIDATION_FAIL_REASON = "validation_fail_reason";
    public static final String VALIDATION_FAIL_REASON_DESC = "validation_fail_reason_desc";

    private Cbtrn02cSchemas() {
    }

    /** TRANFILE (CVTRA05Y) after the run, in TRAN-ID key order. */
    public static DatasetSchema postedTransactions() {
        return DatasetSchema.fromLayout(POSTED_TRANSACTIONS,
                "CBTRN02C TRANFILE: transactions posted by this run (CVTRA05Y), TRAN-ID key order", Copybooks.TRAN_RECORD);
    }

    /** DALYREJS (LRECL 430): the rejected CVTRA06Y record followed by WS-VALIDATION-TRAILER, in write order. */
    public static DatasetSchema dailyRejects() {
        List<Column> cols = new ArrayList<>(DatasetSchema.fromLayout(DAILY_REJECTS, "", Copybooks.DALYTRAN_RECORD).columns());
        cols.add(new Column(VALIDATION_FAIL_REASON, ColumnType.STRING, false,
                "Reject reason code: 0100 card not in xref, 0101 account not found, 0102 overlimit, 0103 after expiration",
                trailerMetadata("WS-VALIDATION-FAIL-REASON", "9(04)", 351, 4)));
        cols.add(new Column(VALIDATION_FAIL_REASON_DESC, ColumnType.STRING, false, "Reject reason text",
                trailerMetadata("WS-VALIDATION-FAIL-REASON-DESC", "X(76)", 355, 76)));
        return new DatasetSchema(DAILY_REJECTS,
                "CBTRN02C DALYREJS: rejected daily transactions with validation trailer, in input order", cols);
    }

    /** ACCTFILE (CVACT01Y) after posting, in ACCT-ID key order. */
    public static DatasetSchema accountMaster() {
        return DatasetSchema.fromLayout(ACCOUNT_MASTER,
                "CBTRN02C ACCTFILE after posting (CVACT01Y), ACCT-ID key order", Copybooks.ACCOUNT_RECORD);
    }

    /** TCATBALF (CVTRA01Y) after posting, in TRAN-CAT-KEY order. */
    public static DatasetSchema tranCatBalance() {
        return DatasetSchema.fromLayout(TRAN_CAT_BALANCE,
                "CBTRN02C TCATBALF after posting (CVTRA01Y), key order acct id + type cd + cat cd", Copybooks.TRAN_CAT_BAL_RECORD);
    }

    public static List<DatasetSchema> all() {
        return List.of(postedTransactions(), dailyRejects(), accountMaster(), tranCatBalance());
    }

    private static Map<String, Object> trailerMetadata(String field, String pic, int offset, int length) {
        Map<String, Object> md = new LinkedHashMap<>();
        md.put("cobol_field", field);
        md.put("copybook", "CBTRN02C WS-VALIDATION-TRAILER");
        md.put("pic", pic);
        md.put("offset", offset);
        md.put("length", length);
        return md;
    }
}
