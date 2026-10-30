package com.carddemo.batch.cbact01c;

import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.ColumnType;
import com.carddemo.batch.common.schema.DatasetSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Output dataset schemas of CBACT01C (contracts in {@code src/main/resources/schemas}). */
public final class Cbact01cSchemas {
    static final ColumnType MONEY = ColumnType.decimal(12, 2);
    private static final String DISPLAY = "DISPLAY";
    private static final String COMP3 = "COMP-3";
    private static final String S9_10_V99 = "S9(10)V99";

    /** OUTFILE (LRECL 107), record OUT-ACCT-REC. */
    public static final DatasetSchema ACCOUNT_OUT = new DatasetSchema("account_out",
            "CBACT01C OUTFILE: one reformatted record per account (OUT-ACCT-REC, LRECL 107)", List.of(
                    col("out_acct_id", ColumnType.STRING, false, "OUTFILE", "OUT-ACCT-ID", "9(11)", DISPLAY, 1, 11),
                    col("out_acct_active_status", ColumnType.STRING, false, "OUTFILE", "OUT-ACCT-ACTIVE-STATUS", "X(01)", DISPLAY, 12, 1),
                    col("out_acct_curr_bal", MONEY, false, "OUTFILE", "OUT-ACCT-CURR-BAL", S9_10_V99, DISPLAY, 13, 12),
                    col("out_acct_credit_limit", MONEY, false, "OUTFILE", "OUT-ACCT-CREDIT-LIMIT", S9_10_V99, DISPLAY, 25, 12),
                    col("out_acct_cash_credit_limit", MONEY, false, "OUTFILE", "OUT-ACCT-CASH-CREDIT-LIMIT", S9_10_V99, DISPLAY, 37, 12),
                    col("out_acct_open_date", ColumnType.STRING, false, "OUTFILE", "OUT-ACCT-OPEN-DATE", "X(10)", DISPLAY, 49, 10),
                    col("out_acct_expiraion_date", ColumnType.STRING, false, "OUTFILE", "OUT-ACCT-EXPIRAION-DATE", "X(10)", DISPLAY, 59, 10),
                    col("out_acct_reissue_date", ColumnType.STRING, false, "OUTFILE", "OUT-ACCT-REISSUE-DATE", "X(10)", DISPLAY, 69, 10),
                    col("out_acct_curr_cyc_credit", MONEY, false, "OUTFILE", "OUT-ACCT-CURR-CYC-CREDIT", S9_10_V99, DISPLAY, 79, 12),
                    col("out_acct_curr_cyc_debit", MONEY, false, "OUTFILE", "OUT-ACCT-CURR-CYC-DEBIT", S9_10_V99, COMP3, 91, 7),
                    col("out_acct_group_id", ColumnType.STRING, false, "OUTFILE", "OUT-ACCT-GROUP-ID", "X(10)", DISPLAY, 98, 10)));

    /** ARRYFILE (LRECL 110), record ARR-ARRAY-REC with ARR-ACCT-BAL OCCURS 5 flattened to _1.._5. */
    public static final DatasetSchema ACCOUNT_ARRAY = new DatasetSchema("account_array",
            "CBACT01C ARRYFILE: ARR-ARRAY-REC (LRECL 110), ARR-ACCT-BAL OCCURS 5 flattened to _1.._5", arrayColumns());

    /** VBRCFILE (RECFM V, 10-80), two records per account: VBRC-REC1 (12 bytes) then VBRC-REC2 (39 bytes). */
    public static final DatasetSchema ACCOUNT_VBRC = new DatasetSchema("account_vbrc",
            "CBACT01C VBRCFILE: variable-length records, VBRC-REC1 (length 12) and VBRC-REC2 (length 39) per account", List.of(
                    col("vbrc_record_type", ColumnType.INT, false, "VBRCFILE", "VBRC-REC1 / VBRC-REC2", "1 or 2", null, 0, 0),
                    col("vbrc_record_length", ColumnType.INT, false, "VBRCFILE", "WS-RECD-LEN", "9(04)", null, 0, 0),
                    col("vbrc_acct_id", ColumnType.STRING, false, "VBRCFILE", "VB1-ACCT-ID / VB2-ACCT-ID", "9(11)", DISPLAY, 1, 11),
                    col("vb1_acct_active_status", ColumnType.STRING, true, "VBRCFILE", "VB1-ACCT-ACTIVE-STATUS", "X(01)", DISPLAY, 12, 1),
                    col("vb2_acct_curr_bal", MONEY, true, "VBRCFILE", "VB2-ACCT-CURR-BAL", S9_10_V99, DISPLAY, 12, 12),
                    col("vb2_acct_credit_limit", MONEY, true, "VBRCFILE", "VB2-ACCT-CREDIT-LIMIT", S9_10_V99, DISPLAY, 24, 12),
                    col("vb2_acct_reissue_yyyy", ColumnType.STRING, true, "VBRCFILE", "VB2-ACCT-REISSUE-YYYY", "X(04)", DISPLAY, 36, 4)));

    /** SYSOUT: every DISPLAY line in order, right-trimmed. */
    public static final DatasetSchema SYSOUT = new DatasetSchema("sysout",
            "CBACT01C SYSOUT: DISPLAY lines in execution order, trailing spaces removed", List.of(
                    Column.of("line_no", ColumnType.INT, "1-based line number"),
                    Column.of("text", ColumnType.STRING, "DISPLAY text")));

    public static final List<DatasetSchema> ALL = List.of(ACCOUNT_OUT, ACCOUNT_ARRAY, ACCOUNT_VBRC, SYSOUT);

    private Cbact01cSchemas() {
    }

    private static List<Column> arrayColumns() {
        List<Column> cols = new ArrayList<>();
        cols.add(col("arr_acct_id", ColumnType.STRING, false, "ARRYFILE", "ARR-ACCT-ID", "9(11)", DISPLAY, 1, 11));
        for (int i = 1; i <= 5; i++) {
            int offset = 12 + (i - 1) * 19;
            cols.add(col("arr_acct_curr_bal_" + i, MONEY, false, "ARRYFILE", "ARR-ACCT-CURR-BAL(" + i + ")", S9_10_V99, DISPLAY, offset, 12));
            cols.add(col("arr_acct_curr_cyc_debit_" + i, MONEY, false, "ARRYFILE", "ARR-ACCT-CURR-CYC-DEBIT(" + i + ")", S9_10_V99, COMP3, offset + 12, 7));
        }
        return cols;
    }

    private static Column col(String name, ColumnType type, boolean nullable, String dd, String cobolField, String pic,
                              String usage, int offset, int length) {
        Map<String, Object> md = new LinkedHashMap<>();
        md.put("cobol_field", cobolField);
        md.put("dd", dd);
        md.put("pic", pic);
        if (usage != null) {
            md.put("usage", usage);
            md.put("offset", offset);
            md.put("length", length);
        }
        return new Column(name, type, nullable, cobolField, md);
    }
}
