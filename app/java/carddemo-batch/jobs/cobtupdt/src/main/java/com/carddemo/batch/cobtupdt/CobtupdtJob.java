package com.carddemo.batch.cobtupdt;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobResult;
import com.carddemo.batch.common.output.DatasetWriter;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.record.CobolRecord;
import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.record.FieldSpec;
import com.carddemo.batch.common.record.FixedWidthFile;
import com.carddemo.batch.common.record.KeyedFile;
import com.carddemo.batch.common.record.RecordLayout;
import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.ColumnType;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * COBTUPDT (JCL MNTTRDB2 STEP1): applies INPFILE add/update/delete maintenance to CARDDEMO.TRANSACTION_TYPE.
 *
 * <p>The Db2 tables are file-backed snapshots: TRANSACTION_TYPE is loaded from DD TRANTYPE (CVTRA03Y unload)
 * into a {@link KeyedFile}, and TRANSACTION_TYPE_CATEGORY (the ON DELETE RESTRICT child) from DD TRANCATG
 * (CVTRA04Y). Each EXEC SQL is modelled by a method returning the SQLCODE Db2 would set.
 */
public final class CobtupdtJob implements BatchJob {

    public static final String PROGRAM_ID = "COBTUPDT";

    public static final int SQL_OK = 0;
    public static final int SQL_NOT_FOUND = 100;
    public static final int SQL_DUPLICATE_KEY = -803;
    public static final int SQL_DELETE_RESTRICTED = -532;

    public static final int RC_ERROR = 4;
    public static final int ABEND_OPEN_FAILED = 999;

    /** FD TR-RECORD / WS-INPUT-REC (53 bytes, RECORDING MODE F). */
    public static final RecordLayout INPUT_RECORD = new RecordLayout("WS-INPUT-REC", "COBTUPDT", List.of(
            FieldSpec.alnum("INPUT-REC-TYPE", 1),
            FieldSpec.alnum("INPUT-REC-NUMBER", 2),
            FieldSpec.alnum("INPUT-REC-DESC", 50)));

    public static final DatasetSchema TRANSACTION_TYPE_SCHEMA = new DatasetSchema("transaction_type",
            "COBTUPDT post-maintenance snapshot of CARDDEMO.TRANSACTION_TYPE, ORDER BY TR_TYPE (Db2 EBCDIC collation, as TRANEXTR)",
            List.of(
                    new Column("tr_type", ColumnType.STRING, false, "Transaction type code",
                            db2("TR_TYPE", "CHAR(2)", "INPUT-REC-NUMBER")),
                    new Column("tr_description", ColumnType.STRING, false, "Transaction type description, right-trimmed",
                            db2("TR_DESCRIPTION", "VARCHAR(50)", "INPUT-REC-DESC"))));

    public static final DatasetSchema MAINTENANCE_LOG_SCHEMA = new DatasetSchema("maintenance_log",
            "COBTUPDT outcome of every INPFILE record, in input order",
            List.of(
                    new Column("record_no", ColumnType.LONG, false, "1-based INPFILE record number", Map.of()),
                    new Column("input_type", ColumnType.STRING, false, "INPUT-REC-TYPE (A, U, D, * or invalid), right-trimmed",
                            input("INPUT-REC-TYPE", 1, 1)),
                    new Column("tr_type", ColumnType.STRING, false, "INPUT-REC-NUMBER, right-trimmed",
                            input("INPUT-REC-NUMBER", 2, 2)),
                    new Column("tr_description", ColumnType.STRING, false, "INPUT-REC-DESC, right-trimmed",
                            input("INPUT-REC-DESC", 4, 50)),
                    new Column("action", ColumnType.STRING, false, "INSERT, UPDATE, DELETE, COMMENT or INVALID", Map.of()),
                    new Column("sqlcode", ColumnType.INT, true, "SQLCODE of the EXEC SQL; null when no SQL ran", Map.of()),
                    new Column("result", ColumnType.STRING, false, "OK, NOT_FOUND, SQL_ERROR, IGNORED or INVALID_TYPE", Map.of()),
                    new Column("message", ColumnType.STRING, false,
                            "Last message DISPLAYed for the record (WS-RETURN-MSG for errors), right-trimmed", Map.of())));

    private static final Charset EBCDIC = Charset.forName("IBM037");
    private static final Comparator<String> EBCDIC_ORDER = (a, b) -> Arrays.compareUnsigned(a.getBytes(EBCDIC), b.getBytes(EBCDIC));

    private static final String TT_KEY = "TRAN-TYPE";
    private static final String TT_DESC = "TRAN-TYPE-DESC";

    @Override
    public String programId() {
        return PROGRAM_ID;
    }

    @Override
    public JobResult run(JobContext ctx) throws Exception {
        return new Run(ctx).execute();
    }

    /** Working storage of one execution. */
    private static final class Run {
        private final JobContext ctx;
        private final PrintStream sysout;
        private final KeyedFile transactionType;
        private final Set<String> categoryParents;
        private final char[] wsReturnMsg = blank(80);
        private final Map<String, Long> counters = new LinkedHashMap<>();
        private DatasetWriter log;
        private int returnCode;
        private long recordNo;
        private String lastDisplay;

        Run(JobContext ctx) {
            this.ctx = ctx;
            this.sysout = ctx.sysout();
            this.transactionType = loadTransactionType(ctx.input("TRANTYPE"));
            this.categoryParents = KeyedFile.load(ctx.input("TRANCATG"), Copybooks.TRAN_CAT_RECORD, "TRAN-TYPE-CD", "TRAN-CAT-CD")
                    .inKeyOrder().stream().map(r -> r.getString("TRAN-TYPE-CD")).collect(Collectors.toSet());
            for (String c : List.of("records_read", "comments_ignored", "records_inserted", "records_updated", "records_deleted",
                    "not_found", "sql_errors", "invalid_type", "table_rows", "errors")) {
                counters.put(c, 0L);
            }
        }

        JobResult execute() {
            Path inpfile = ctx.input("INPFILE");
            // 0001-OPEN-FILES falls through into 1001-READ-NEXT-RECORDS (no PERFORM, no STOP RUN between them).
            openFiles(inpfile);
            try (DatasetWriter maintenanceLog = ctx.openDataset("maintenance_log", MAINTENANCE_LOG_SCHEMA, OutputFormat.CSV)) {
                log = maintenanceLog;
                readNextRecords(inpfile);
            }
            writeSnapshot();
            counters.put("errors", counters.get("not_found") + counters.get("sql_errors") + counters.get("invalid_type"));
            return JobResult.of(returnCode, counters);
        }

        // 0001-OPEN-FILES
        private void openFiles(Path inpfile) {
            if (Files.isRegularFile(inpfile) && Files.isReadable(inpfile)) {
                display("OPEN FILE OK");
            } else {
                display("OPEN FILE NOT OK");
                // The COBOL falls through and READs the unopened file forever (status 47, never AT END).
                throw new AbendException(ABEND_OPEN_FAILED, "INPFILE could not be opened: " + inpfile);
            }
        }

        // 1001-READ-NEXT-RECORDS
        private void readNextRecords(Path inpfile) {
            try (BufferedReader in = Files.newBufferedReader(inpfile, StandardCharsets.US_ASCII)) {
                CobolRecord rec = readRecords(in);
                while (rec != null) {
                    treatRecord(rec);
                    rec = readRecords(in);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            closeStop();
        }

        // 1002-READ-RECORDS: one line per 53-byte FB record; an empty line is an all-spaces record.
        private CobolRecord readRecords(BufferedReader in) throws IOException {
            String line = in.readLine();
            if (line == null) {
                return null;
            }
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            recordNo++;
            CobolRecord rec;
            try {
                rec = INPUT_RECORD.parse(line);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("INPFILE:" + recordNo + ": " + e.getMessage(), e);
            }
            counters.merge("records_read", 1L, Long::sum);
            display("PROCESSING   " + rec.toFixedWidth());
            return rec;
        }

        // 1003-TREAT-RECORD
        private void treatRecord(CobolRecord rec) {
            String type = rec.getString("INPUT-REC-TYPE");
            switch (type) {
                case "A" -> {
                    display("ADDING RECORD");
                    insertDb(rec);
                }
                case "U" -> {
                    display("UPDATING RECORD");
                    updateDb(rec);
                }
                case "D" -> {
                    display("DELETING RECORD");
                    deleteDb(rec);
                }
                case "*" -> {
                    display("IGNORING COMMENTED LINE");
                    logRow(rec, "COMMENT", null, "IGNORED", "comments_ignored");
                }
                default -> {
                    stringIntoReturnMsg("ERROR: TYPE NOT VALID");
                    abend();
                    logRow(rec, "INVALID", null, "INVALID_TYPE", "invalid_type");
                }
            }
        }

        // 10031-INSERT-DB
        private void insertDb(CobolRecord rec) {
            int sqlcode = sqlInsert(rec.getString("INPUT-REC-NUMBER"), rec.getString("INPUT-REC-DESC"));
            if (sqlcode == SQL_OK) {
                display("RECORD INSERTED SUCCESSFULLY");
            } else if (sqlcode < 0) {
                sqlErrorMessage(sqlcode);
            }
            logSql(rec, "INSERT", sqlcode, "records_inserted");
        }

        // 10032-UPDATE-DB
        private void updateDb(CobolRecord rec) {
            int sqlcode = sqlUpdate(rec.getString("INPUT-REC-NUMBER"), rec.getString("INPUT-REC-DESC"));
            if (sqlcode == SQL_OK) {
                display("RECORD UPDATED SUCCESSFULLY");
            } else if (sqlcode == SQL_NOT_FOUND) {
                notFoundMessage();
            } else if (sqlcode < 0) {
                sqlErrorMessage(sqlcode);
            }
            logSql(rec, "UPDATE", sqlcode, "records_updated");
        }

        // 10033-DELETE-DB
        private void deleteDb(CobolRecord rec) {
            int sqlcode = sqlDelete(rec.getString("INPUT-REC-NUMBER"));
            if (sqlcode == SQL_OK) {
                display("RECORD DELETED SUCCESSFULLY");
            } else if (sqlcode == SQL_NOT_FOUND) {
                notFoundMessage();
            } else if (sqlcode < 0) {
                sqlErrorMessage(sqlcode);
            }
            logSql(rec, "DELETE", sqlcode, "records_deleted");
        }

        // 9999-ABEND: DISPLAY WS-RETURN-MSG, MOVE 4 TO RETURN-CODE, then EXIT back to the caller (no stop).
        private void abend() {
            display(new String(wsReturnMsg));
            returnCode = RC_ERROR;
        }

        // 2001-CLOSE-STOP
        private void closeStop() {
            // CLOSE TR-RECORD: the reader is closed by readNextRecords.
        }

        /** EXEC SQL INSERT INTO CARDDEMO.TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) VALUES (:INPUT-REC-NUMBER, :INPUT-REC-DESC). */
        int sqlInsert(String trType, String trDescription) {
            CobolRecord row = Copybooks.TRAN_TYPE_RECORD.newRecord().set(TT_KEY, trType).set(TT_DESC, trDescription);
            return transactionType.write(row) == KeyedFile.Status.OK ? SQL_OK : SQL_DUPLICATE_KEY;
        }

        /** EXEC SQL UPDATE CARDDEMO.TRANSACTION_TYPE SET TR_DESCRIPTION = :INPUT-REC-DESC WHERE TR_TYPE = :INPUT-REC-NUMBER. */
        int sqlUpdate(String trType, String trDescription) {
            return transactionType.read(transactionType.key(trType))
                    .map(row -> {
                        transactionType.rewrite(row.set(TT_DESC, trDescription));
                        return SQL_OK;
                    })
                    .orElse(SQL_NOT_FOUND);
        }

        /** EXEC SQL DELETE FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :INPUT-REC-NUMBER (FK ON DELETE RESTRICT). */
        int sqlDelete(String trType) {
            String key = transactionType.key(trType);
            if (transactionType.read(key).isEmpty()) {
                return SQL_NOT_FOUND;
            }
            if (categoryParents.contains(key)) {
                return SQL_DELETE_RESTRICTED;
            }
            transactionType.delete(key);
            return SQL_OK;
        }

        private void notFoundMessage() {
            stringIntoReturnMsg("No records found.");
            abend();
        }

        private void sqlErrorMessage(int sqlcode) {
            // MOVE SQLCODE TO WS-VAR-SQLCODE (PIC ----9)
            stringIntoReturnMsg("Error accessing:" + " TRANSACTION_TYPE table. SQLCODE:" + editSqlcode(sqlcode));
            abend();
        }

        /** STRING ... DELIMITED BY SIZE INTO WS-RETURN-MSG: overwrites from position 1, the rest is left as it was. */
        private void stringIntoReturnMsg(String text) {
            text.getChars(0, Math.min(text.length(), wsReturnMsg.length), wsReturnMsg, 0);
        }

        private void logSql(CobolRecord rec, String action, int sqlcode, String okCounter) {
            String result = sqlcode == SQL_OK ? "OK" : sqlcode == SQL_NOT_FOUND ? "NOT_FOUND" : "SQL_ERROR";
            String counter = sqlcode == SQL_OK ? okCounter : sqlcode == SQL_NOT_FOUND ? "not_found" : "sql_errors";
            logRow(rec, action, sqlcode, result, counter);
        }

        private void logRow(CobolRecord rec, String action, Integer sqlcode, String result, String counter) {
            counters.merge(counter, 1L, Long::sum);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("record_no", recordNo);
            row.put("input_type", rec.getTrimmed("INPUT-REC-TYPE"));
            row.put("tr_type", rec.getTrimmed("INPUT-REC-NUMBER"));
            row.put("tr_description", rec.getTrimmed("INPUT-REC-DESC"));
            row.put("action", action);
            row.put("sqlcode", sqlcode);
            row.put("result", result);
            row.put("message", lastDisplay);
            log.write(row);
        }

        private void writeSnapshot() {
            List<CobolRecord> rows = transactionType.inKeyOrder().stream()
                    .sorted(Comparator.comparing(r -> r.getString(TT_KEY), EBCDIC_ORDER))
                    .toList();
            try (DatasetWriter out = ctx.openDataset("transaction_type", TRANSACTION_TYPE_SCHEMA, OutputFormat.PARQUET)) {
                for (CobolRecord r : rows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tr_type", r.getTrimmed(TT_KEY));
                    row.put("tr_description", r.getTrimmed(TT_DESC));
                    out.write(row);
                }
            }
            counters.put("table_rows", (long) rows.size());
        }

        private void display(String line) {
            sysout.println(line);
            lastDisplay = rtrim(line);
        }
    }

    /** Initial TRANSACTION_TYPE content: TRAN-TYPE to TR_TYPE, TRAN-TYPE-DESC to TR_DESCRIPTION. */
    static KeyedFile loadTransactionType(Path trantype) {
        KeyedFile table = new KeyedFile(Copybooks.TRAN_TYPE_RECORD, TT_KEY);
        FixedWidthFile.forEach(trantype, Copybooks.TRAN_TYPE_RECORD, rec -> {
            if (table.write(rec) != KeyedFile.Status.OK) {
                throw new IllegalArgumentException(trantype.getFileName() + ": duplicate TR_TYPE " + table.keyOf(rec));
            }
        });
        return table;
    }

    /** MOVE to PIC ----9: five positions, floating minus sign, no sign for positive values. */
    static String editSqlcode(int value) {
        String digits = Integer.toString(Math.abs(value) % 100000);
        String s = value < 0 ? "-" + digits : digits;
        if (s.length() > 5) {
            s = s.substring(s.length() - 5);
        }
        return " ".repeat(5 - s.length()) + s;
    }

    static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }

    private static char[] blank(int n) {
        char[] c = new char[n];
        Arrays.fill(c, ' ');
        return c;
    }

    private static Map<String, Object> db2(String column, String type, String hostVariable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("db2_table", "CARDDEMO.TRANSACTION_TYPE");
        m.put("db2_column", column);
        m.put("db2_type", type);
        m.put("host_variable", hostVariable);
        return m;
    }

    private static Map<String, Object> input(String field, int offset, int length) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cobol_field", field);
        m.put("dd", "INPFILE");
        m.put("offset", offset);
        m.put("length", length);
        return m;
    }
}
