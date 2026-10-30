package com.carddemo.batch.cbtrn02c;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobResult;
import com.carddemo.batch.common.output.DatasetWriter;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.record.CobolRecord;
import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.record.FixedWidthFile;
import com.carddemo.batch.common.record.KeyedFile;
import com.carddemo.batch.common.record.ZonedDecimal;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * CBTRN02C (JCL POSTTRAN, STEP15): validates the daily transactions and posts the valid ones to the
 * transaction category balances, the account master and the transaction file; rejects go to DALYREJS.
 * Paragraph-per-method translation of {@code app/cbl/CBTRN02C.cbl}; rules in
 * {@code docs/modernization/business-rules/CBTRN02C.md}.
 */
public final class Cbtrn02cJob implements BatchJob {

    public static final String PROGRAM_ID = "CBTRN02C";
    public static final String COUNTER_PROCESSED = "transactions_processed";
    public static final String COUNTER_REJECTED = "transactions_rejected";

    static final String DD_DALYTRAN = "DALYTRAN";
    static final String DD_XREFFILE = "XREFFILE";
    static final String DD_ACCTFILE = "ACCTFILE";
    static final String DD_TCATBALF = "TCATBALF";

    @Override
    public String programId() {
        return PROGRAM_ID;
    }

    @Override
    public JobResult run(JobContext context) {
        return new Run(context).procedureDivision();
    }

    /** One execution: WORKING-STORAGE plus the open files. */
    static final class Run {

        private final JobContext ctx;
        private final PrintStream sysout;

        // Files
        private Iterator<CobolRecord> dalytranFile;
        private KeyedFile transactFile;
        private KeyedFile xrefFile;
        private DatasetWriter dalyrejsFile;
        private KeyedFile accountFile;
        private KeyedFile tcatbalFile;

        // WORKING-STORAGE
        private CobolRecord dalytranRecord = Copybooks.DALYTRAN_RECORD.newRecord();
        private CobolRecord cardXrefRecord = Copybooks.CARD_XREF_RECORD.newRecord();
        private CobolRecord accountRecord = Copybooks.ACCOUNT_RECORD.newRecord();
        private CobolRecord tranCatBalRecord = Copybooks.TRAN_CAT_BAL_RECORD.newRecord();
        private boolean endOfFile;
        private long wsTransactionCount;
        private long wsRejectCount;
        private int wsValidationFailReason;
        private String wsValidationFailReasonDesc = "";
        private BigDecimal wsTempBal;
        private boolean wsCreateTrancatRec;

        Run(JobContext ctx) {
            this.ctx = ctx;
            this.sysout = ctx.sysout();
        }

        // PROCEDURE DIVISION (main line)
        JobResult procedureDivision() {
            display("START OF EXECUTION OF PROGRAM CBTRN02C");
            dalytranOpen();
            tranfileOpen();
            xreffileOpen();
            dalyrejsOpen();
            acctfileOpen();
            tcatbalfOpen();
            while (!endOfFile) {
                dalytranGetNext();
                if (!endOfFile) {
                    wsTransactionCount = addCounter(wsTransactionCount);
                    wsValidationFailReason = 0;
                    wsValidationFailReasonDesc = "";
                    validateTran();
                    if (wsValidationFailReason == 0) {
                        postTransaction();
                    } else {
                        wsRejectCount = addCounter(wsRejectCount);
                        writeRejectRec();
                    }
                }
            }
            dalytranClose();
            tranfileClose();
            xreffileClose();
            dalyrejsClose();
            acctfileClose();
            tcatbalfClose();
            display("TRANSACTIONS PROCESSED :" + counter(wsTransactionCount));
            display("TRANSACTIONS REJECTED  :" + counter(wsRejectCount));
            int returnCode = wsRejectCount > 0 ? 4 : 0;
            display("END OF EXECUTION OF PROGRAM CBTRN02C");
            Map<String, Long> counters = new LinkedHashMap<>();
            counters.put(COUNTER_PROCESSED, wsTransactionCount);
            counters.put(COUNTER_REJECTED, wsRejectCount);
            return JobResult.of(returnCode, counters);
        }

        // 0000-DALYTRAN-OPEN
        void dalytranOpen() {
            Path p = openInput(DD_DALYTRAN, "ERROR OPENING DALYTRAN");
            dalytranFile = FixedWidthFile.read(p, Copybooks.DALYTRAN_RECORD).iterator();
        }

        // 0100-TRANFILE-OPEN (OPEN OUTPUT: the KSDS starts empty)
        void tranfileOpen() {
            transactFile = new KeyedFile(Copybooks.TRAN_RECORD, "TRAN-ID");
        }

        // 0200-XREFFILE-OPEN
        void xreffileOpen() {
            xrefFile = KeyedFile.load(openInput(DD_XREFFILE, "ERROR OPENING CROSS REF FILE"),
                    Copybooks.CARD_XREF_RECORD, "XREF-CARD-NUM");
        }

        // 0300-DALYREJS-OPEN
        void dalyrejsOpen() {
            dalyrejsFile = ctx.openDataset(Cbtrn02cSchemas.DAILY_REJECTS, Cbtrn02cSchemas.dailyRejects(), OutputFormat.PARQUET);
        }

        // 0400-ACCTFILE-OPEN (I-O)
        void acctfileOpen() {
            accountFile = KeyedFile.load(openInput(DD_ACCTFILE, "ERROR OPENING ACCOUNT MASTER FILE"),
                    Copybooks.ACCOUNT_RECORD, "ACCT-ID");
        }

        // 0500-TCATBALF-OPEN (I-O)
        void tcatbalfOpen() {
            tcatbalFile = KeyedFile.load(openInput(DD_TCATBALF, "ERROR OPENING TRANSACTION BALANCE FILE"),
                    Copybooks.TRAN_CAT_BAL_RECORD, "TRANCAT-ACCT-ID", "TRANCAT-TYPE-CD", "TRANCAT-CD");
        }

        // 1000-DALYTRAN-GET-NEXT
        void dalytranGetNext() {
            if (dalytranFile.hasNext()) {
                dalytranRecord = dalytranFile.next();
            } else {
                endOfFile = true;
            }
        }

        // 1500-VALIDATE-TRAN
        void validateTran() {
            lookupXref();
            if (wsValidationFailReason == 0) {
                lookupAcct();
            }
        }

        // 1500-A-LOOKUP-XREF
        void lookupXref() {
            Optional<CobolRecord> xref = xrefFile.read(xrefFile.key(dalytranRecord.getString("DALYTRAN-CARD-NUM")));
            if (xref.isEmpty()) {
                fail(100, "INVALID CARD NUMBER FOUND");
            } else {
                cardXrefRecord = xref.get();
            }
        }

        // 1500-B-LOOKUP-ACCT
        void lookupAcct() {
            Optional<CobolRecord> acct = accountFile.read(accountFile.key(cardXrefRecord.getString("XREF-ACCT-ID")));
            if (acct.isEmpty()) {
                fail(101, "ACCOUNT RECORD NOT FOUND");
                return;
            }
            accountRecord = acct.get();
            wsTempBal = picture(accountRecord.getDecimal("ACCT-CURR-CYC-CREDIT")
                    .subtract(accountRecord.getDecimal("ACCT-CURR-CYC-DEBIT"))
                    .add(dalytranRecord.getDecimal("DALYTRAN-AMT")), 9, 2);
            if (accountRecord.getDecimal("ACCT-CREDIT-LIMIT").compareTo(wsTempBal) < 0) {
                fail(102, "OVERLIMIT TRANSACTION");
            }
            String origDate = dalytranRecord.getString("DALYTRAN-ORIG-TS").substring(0, 10);
            if (accountRecord.getString("ACCT-EXPIRAION-DATE").compareTo(origDate) < 0) {
                fail(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
            }
        }

        // 2000-POST-TRANSACTION
        void postTransaction() {
            CobolRecord tran = Copybooks.TRAN_RECORD.newRecord();
            tran.set("TRAN-ID", dalytranRecord.get("DALYTRAN-ID"));
            tran.set("TRAN-TYPE-CD", dalytranRecord.get("DALYTRAN-TYPE-CD"));
            tran.set("TRAN-CAT-CD", dalytranRecord.get("DALYTRAN-CAT-CD"));
            tran.set("TRAN-SOURCE", dalytranRecord.get("DALYTRAN-SOURCE"));
            tran.set("TRAN-DESC", dalytranRecord.get("DALYTRAN-DESC"));
            tran.set("TRAN-AMT", dalytranRecord.get("DALYTRAN-AMT"));
            tran.set("TRAN-MERCHANT-ID", dalytranRecord.get("DALYTRAN-MERCHANT-ID"));
            tran.set("TRAN-MERCHANT-NAME", dalytranRecord.get("DALYTRAN-MERCHANT-NAME"));
            tran.set("TRAN-MERCHANT-CITY", dalytranRecord.get("DALYTRAN-MERCHANT-CITY"));
            tran.set("TRAN-MERCHANT-ZIP", dalytranRecord.get("DALYTRAN-MERCHANT-ZIP"));
            tran.set("TRAN-CARD-NUM", dalytranRecord.get("DALYTRAN-CARD-NUM"));
            tran.set("TRAN-ORIG-TS", dalytranRecord.get("DALYTRAN-ORIG-TS"));
            tran.set("TRAN-PROC-TS", getDb2FormatTimestamp());
            updateTcatbal();
            updateAccountRec();
            writeTransactionFile(tran);
        }

        // 2500-WRITE-REJECT-REC
        void writeRejectRec() {
            Map<String, Object> row = dalytranRecord.toColumns();
            row.put(Cbtrn02cSchemas.VALIDATION_FAIL_REASON, String.format("%04d", wsValidationFailReason % 10000));
            row.put(Cbtrn02cSchemas.VALIDATION_FAIL_REASON_DESC, rtrim(wsValidationFailReasonDesc));
            dalyrejsFile.write(row);
        }

        // 2700-UPDATE-TCATBAL
        void updateTcatbal() {
            String key = tcatbalFile.key(cardXrefRecord.getString("XREF-ACCT-ID"),
                    dalytranRecord.getString("DALYTRAN-TYPE-CD"), dalytranRecord.getString("DALYTRAN-CAT-CD"));
            wsCreateTrancatRec = false;
            Optional<CobolRecord> found = tcatbalFile.read(key);
            if (found.isEmpty()) {
                display("TCATBAL record not found for key : " + key + ".. Creating.");
                wsCreateTrancatRec = true;
            } else {
                tranCatBalRecord = found.get();
            }
            if (wsCreateTrancatRec) {
                createTcatbalRec();
            } else {
                updateTcatbalRec();
            }
        }

        // 2700-A-CREATE-TCATBAL-REC
        void createTcatbalRec() {
            tranCatBalRecord = Copybooks.TRAN_CAT_BAL_RECORD.newRecord();
            tranCatBalRecord.set("TRANCAT-ACCT-ID", cardXrefRecord.get("XREF-ACCT-ID"));
            tranCatBalRecord.set("TRANCAT-TYPE-CD", dalytranRecord.get("DALYTRAN-TYPE-CD"));
            tranCatBalRecord.set("TRANCAT-CD", dalytranRecord.get("DALYTRAN-CAT-CD"));
            addAmount(tranCatBalRecord, "TRAN-CAT-BAL");
            if (tcatbalFile.write(tranCatBalRecord) != KeyedFile.Status.OK) {
                ioError("ERROR WRITING TRANSACTION BALANCE FILE", "22");
            }
        }

        // 2700-B-UPDATE-TCATBAL-REC
        void updateTcatbalRec() {
            addAmount(tranCatBalRecord, "TRAN-CAT-BAL");
            if (tcatbalFile.rewrite(tranCatBalRecord) != KeyedFile.Status.OK) {
                ioError("ERROR REWRITING TRANSACTION BALANCE FILE", "23");
            }
        }

        // 2800-UPDATE-ACCOUNT-REC
        void updateAccountRec() {
            addAmount(accountRecord, "ACCT-CURR-BAL");
            if (dalytranRecord.getDecimal("DALYTRAN-AMT").signum() >= 0) {
                addAmount(accountRecord, "ACCT-CURR-CYC-CREDIT");
            } else {
                addAmount(accountRecord, "ACCT-CURR-CYC-DEBIT");
            }
            if (accountFile.rewrite(accountRecord) != KeyedFile.Status.OK) {
                // INVALID KEY only sets reason 109; nothing reads it afterwards (see business rules).
                wsValidationFailReason = 109;
                wsValidationFailReasonDesc = "ACCOUNT RECORD NOT FOUND";
            }
        }

        // 2900-WRITE-TRANSACTION-FILE
        void writeTransactionFile(CobolRecord tran) {
            if (transactFile.write(tran) != KeyedFile.Status.OK) {
                ioError("ERROR WRITING TO TRANSACTION FILE", "22");
            }
        }

        // 9000-DALYTRAN-CLOSE
        void dalytranClose() {
            dalytranFile = null;
        }

        // 9100-TRANFILE-CLOSE: the KSDS content, in key order
        void tranfileClose() {
            writeAll(Cbtrn02cSchemas.POSTED_TRANSACTIONS, Cbtrn02cSchemas.postedTransactions(), transactFile);
        }

        // 9200-XREFFILE-CLOSE
        void xreffileClose() {
            xrefFile = null;
        }

        // 9300-DALYREJS-CLOSE
        void dalyrejsClose() {
            dalyrejsFile.close();
        }

        // 9400-ACCTFILE-CLOSE
        void acctfileClose() {
            writeAll(Cbtrn02cSchemas.ACCOUNT_MASTER, Cbtrn02cSchemas.accountMaster(), accountFile);
        }

        // 9500-TCATBALF-CLOSE
        void tcatbalfClose() {
            writeAll(Cbtrn02cSchemas.TRAN_CAT_BALANCE, Cbtrn02cSchemas.tranCatBalance(), tcatbalFile);
        }

        // Z-GET-DB2-FORMAT-TIMESTAMP: YYYY-MM-DD-HH.MM.SS.hh0000 from FUNCTION CURRENT-DATE (hundredths + '0000')
        String getDb2FormatTimestamp() {
            return db2FormatTimestamp(ctx.clock().instant());
        }

        // 9999-ABEND-PROGRAM
        void abendProgram() {
            display("ABENDING PROGRAM");
            throw new AbendException(999, "CBTRN02C abend");
        }

        // 9910-DISPLAY-IO-STATUS (numeric statuses only; the binary form for '9x' is not reachable here)
        void displayIoStatus(String ioStatus) {
            display("FILE STATUS IS: NNNN00" + ioStatus);
        }

        private Path openInput(String dd, String errorMessage) {
            Optional<Path> p = ctx.optionalInput(dd);
            if (p.isEmpty() || !Files.isRegularFile(p.get())) {
                ioError(errorMessage, "35");
            }
            return p.get();
        }

        private void ioError(String message, String status) {
            display(message);
            displayIoStatus(status);
            abendProgram();
        }

        private void fail(int reason, String desc) {
            wsValidationFailReason = reason;
            wsValidationFailReasonDesc = desc;
        }

        /** ADD DALYTRAN-AMT TO field: no ON SIZE ERROR, so high-order digits are truncated by the picture. */
        private void addAmount(CobolRecord rec, String field) {
            rec.set(field, rec.getDecimal(field).add(dalytranRecord.getDecimal("DALYTRAN-AMT")));
        }

        private void writeAll(String dataset, DatasetSchema schema, KeyedFile file) {
            try (DatasetWriter w = ctx.openDataset(dataset, schema, OutputFormat.PARQUET)) {
                for (CobolRecord rec : file.inKeyOrder()) {
                    w.write(rec.toColumns());
                }
            }
        }

        private void display(String line) {
            sysout.println(line);
        }
    }

    /** Stores {@code value} into {@code PIC S9(intDigits)V9(scale)} as COMPUTE/ADD without ON SIZE ERROR does. */
    /** Z-GET-DB2-FORMAT-TIMESTAMP layout: CURRENT-DATE hundredths followed by literal '0000'. */
    static String db2FormatTimestamp(java.time.Instant instant) {
        LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", now.getYear(), now.getMonthValue(),
                now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano() / 10_000_000);
    }

    static BigDecimal picture(BigDecimal value, int intDigits, int scale) {
        return ZonedDecimal.decode(ZonedDecimal.encode(value, intDigits, scale, true), scale, true);
    }

    /** PIC 9(09) counter: ADD 1 wraps at 10^9 (high-order truncation). */
    static long addCounter(long counter) {
        return (counter + 1) % 1_000_000_000L;
    }

    static String counter(long value) {
        return String.format("%09d", value);
    }

    static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}
