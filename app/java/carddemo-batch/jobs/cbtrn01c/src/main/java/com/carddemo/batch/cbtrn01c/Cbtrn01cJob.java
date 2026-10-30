package com.carddemo.batch.cbtrn01c;

import static com.carddemo.batch.common.record.FieldSpec.alnum;

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
import com.carddemo.batch.common.record.RecordLayout;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CBTRN01C: verifies each daily transaction (DALYTRAN) against the card cross-reference (XREFFILE)
 * and the account master (ACCTFILE), DISPLAYing the outcome. Read-only; RETURN-CODE is always 0
 * unless an OPEN or READ fails, which abends (U999). See docs/modernization/business-rules/CBTRN01C.md.
 */
public final class Cbtrn01cJob implements BatchJob {

    public static final String PROGRAM_ID = "CBTRN01C";
    public static final int ABEND_CODE = 999;

    public static final String DALYTRAN = "DALYTRAN";
    public static final String CUSTFILE = "CUSTFILE";
    public static final String XREFFILE = "XREFFILE";
    public static final String CARDFILE = "CARDFILE";
    public static final String ACCTFILE = "ACCTFILE";
    public static final String TRANFILE = "TRANFILE";

    /** FD-TRAN-RECORD: the program reads DALYTRAN as 16 + 334 bytes and moves it INTO DALYTRAN-RECORD. */
    static final RecordLayout FD_TRAN_RECORD = new RecordLayout("FD-TRAN-RECORD", "CBTRN01C",
            List.of(alnum("FD-TRAN-ID", 16), alnum("FD-CUST-DATA", 334)));

    static final String STATUS_OK = "00";
    static final String STATUS_EOF = "10";
    static final String STATUS_RECORD_LENGTH = "04";
    static final String STATUS_NOT_FOUND = "35";

    @Override
    public String programId() {
        return PROGRAM_ID;
    }

    @Override
    public JobResult run(JobContext ctx) {
        return new Execution(ctx).mainPara();
    }

    /** One run of the program: WORKING-STORAGE plus the PROCEDURE DIVISION paragraphs. */
    private static final class Execution {

        private static final RecordLayout DALYTRAN_RECORD = Copybooks.DALYTRAN_RECORD;

        private final JobContext ctx;
        private final SysoutDataset sysout;
        private final DatasetWriter verification;

        // Files
        private List<CobolRecord> dalytranFile;
        private int dalytranNext;
        private boolean dalytranReadError;
        private KeyedFile xrefFile;
        private KeyedFile accountFile;

        // WORKING-STORAGE
        /** DALYTRAN-RECORD (CVTRA06Y) image; no VALUE clause, so spaces/zeros as GnuCOBOL initialises it. */
        private String dalytranRecord = DALYTRAN_RECORD.newRecord().toFixedWidth();
        private long dalytranRecordNo;
        private String endOfDailyTransFile = "N";
        private int wsXrefReadStatus;
        private int wsAcctReadStatus;
        private String xrefCardNum;
        private CobolRecord cardXrefRecord;
        private String acctId;

        // Counters (JobResult / manifest)
        private long lookups;
        private long verified;
        private long cardNotVerified;
        private long accountNotFound;
        private long eofReprocessed;

        Execution(JobContext ctx) {
            this.ctx = ctx;
            this.sysout = new SysoutDataset(ctx);
            this.verification = ctx.openDataset(Cbtrn01cDatasets.DAILY_TRAN_VERIFICATION,
                    Cbtrn01cDatasets.DAILY_TRAN_VERIFICATION_SCHEMA, OutputFormat.PARQUET);
        }

        // MAIN-PARA
        JobResult mainPara() {
            display("START OF EXECUTION OF PROGRAM CBTRN01C");
            dalytranOpen();
            custfileOpen();
            xreffileOpen();
            cardfileOpen();
            acctfileOpen();
            tranfileOpen();

            while (!"Y".equals(endOfDailyTransFile)) {
                if ("N".equals(endOfDailyTransFile)) {
                    dalytranGetNext();
                    boolean atEof = !"N".equals(endOfDailyTransFile);
                    if (!atEof) {
                        display(dalytranRecord);
                    }
                    // Quirk (BR-CBTRN01C-07): the lookup is outside the inner IF, so it also runs at EOF
                    // with the last record still in DALYTRAN-RECORD.
                    wsXrefReadStatus = 0;
                    xrefCardNum = dalytranField("DALYTRAN-CARD-NUM");
                    lookupXref();
                    Integer acctStatus = null;
                    String status;
                    if (wsXrefReadStatus == 0) {
                        wsAcctReadStatus = 0;
                        acctId = cardXrefRecord.getString("XREF-ACCT-ID");
                        readAccount();
                        acctStatus = wsAcctReadStatus;
                        if (wsAcctReadStatus != 0) {
                            display("ACCOUNT " + acctId + " NOT FOUND");
                            status = Cbtrn01cDatasets.ACCOUNT_NOT_FOUND;
                            accountNotFound++;
                        } else {
                            status = Cbtrn01cDatasets.VERIFIED;
                            verified++;
                        }
                    } else {
                        display("CARD NUMBER " + dalytranField("DALYTRAN-CARD-NUM")
                                + " COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-" + dalytranField("DALYTRAN-ID"));
                        status = Cbtrn01cDatasets.CARD_NOT_VERIFIED;
                        cardNotVerified++;
                    }
                    writeVerification(atEof, acctStatus, status);
                }
            }

            closeFiles();
            display("END OF EXECUTION OF PROGRAM CBTRN01C");

            Map<String, Long> counters = new LinkedHashMap<>();
            counters.put("records_read", dalytranRecordNo);
            counters.put("lookups", lookups);
            counters.put("verified", verified);
            counters.put("card_not_verified", cardNotVerified);
            counters.put("account_not_found", accountNotFound);
            counters.put("eof_reprocessed", eofReprocessed);
            return JobResult.ok(counters);
        }

        // 1000-DALYTRAN-GET-NEXT
        private void dalytranGetNext() {
            String status;
            if (dalytranNext < dalytranFile.size()) {
                // READ ... INTO: DALYTRAN-RECORD changes only on a successful read.
                dalytranRecord = dalytranFile.get(dalytranNext++).toFixedWidth();
                dalytranRecordNo++;
                status = STATUS_OK;
            } else {
                status = dalytranReadError ? STATUS_RECORD_LENGTH : STATUS_EOF;
            }
            if (STATUS_EOF.equals(status)) {
                endOfDailyTransFile = "Y";
            } else if (!STATUS_OK.equals(status)) {
                display("ERROR READING DAILY TRANSACTION FILE");
                zDisplayIoStatus(status);
                zAbendProgram();
            }
        }

        // 2000-LOOKUP-XREF
        private void lookupXref() {
            Optional<CobolRecord> rec = xrefFile.read(xrefCardNum);
            if (rec.isEmpty()) {
                display("INVALID CARD NUMBER FOR XREF");
                wsXrefReadStatus = 4;
            } else {
                cardXrefRecord = rec.get();
                display("SUCCESSFUL READ OF XREF");
                display("CARD NUMBER: " + cardXrefRecord.getString("XREF-CARD-NUM"));
                display("ACCOUNT ID : " + cardXrefRecord.getString("XREF-ACCT-ID"));
                display("CUSTOMER ID: " + cardXrefRecord.getString("XREF-CUST-ID"));
            }
        }

        // 3000-READ-ACCOUNT
        private void readAccount() {
            if (accountFile.read(acctId).isEmpty()) {
                display("INVALID ACCOUNT NUMBER FOUND");
                wsAcctReadStatus = 4;
            } else {
                display("SUCCESSFUL READ OF ACCOUNT FILE");
            }
        }

        // 0000-DALYTRAN-OPEN
        private void dalytranOpen() {
            Path file = openInput(DALYTRAN, "ERROR OPENING DAILY TRANSACTION FILE");
            List<CobolRecord> records = new ArrayList<>();
            try {
                FixedWidthFile.forEach(file, FD_TRAN_RECORD, records::add);
            } catch (IllegalArgumentException | UncheckedIOException e) {
                // Records before the bad one are still read normally; the bad READ fails (status 04).
                dalytranReadError = true;
            }
            dalytranFile = records;
        }

        // 0100-CUSTFILE-OPEN (opened, never read)
        private void custfileOpen() {
            openInput(CUSTFILE, "ERROR OPENING CUSTOMER FILE");
        }

        // 0200-XREFFILE-OPEN
        private void xreffileOpen() {
            Path file = openInput(XREFFILE, "ERROR OPENING CROSS REF FILE");
            xrefFile = KeyedFile.load(file, Copybooks.CARD_XREF_RECORD, "XREF-CARD-NUM");
        }

        // 0300-CARDFILE-OPEN (opened, never read)
        private void cardfileOpen() {
            openInput(CARDFILE, "ERROR OPENING CARD FILE");
        }

        // 0400-ACCTFILE-OPEN
        private void acctfileOpen() {
            Path file = openInput(ACCTFILE, "ERROR OPENING ACCOUNT FILE");
            accountFile = KeyedFile.load(file, Copybooks.ACCOUNT_RECORD, "ACCT-ID");
        }

        // 0500-TRANFILE-OPEN (opened, never read)
        private void tranfileOpen() {
            openInput(TRANFILE, "ERROR OPENING TRANSACTION FILE");
        }

        /** OPEN INPUT: a DD that is not supplied, or whose file does not exist, is file status 35. */
        private Path openInput(String ddName, String errorMessage) {
            Optional<Path> file = ctx.optionalInput(ddName).filter(Files::isRegularFile);
            if (file.isEmpty()) {
                display(errorMessage);
                zDisplayIoStatus(STATUS_NOT_FOUND);
                zAbendProgram();
            }
            return file.get();
        }

        // 9000-DALYTRAN-CLOSE .. 9500-TRANFILE-CLOSE: closing an in-memory file cannot fail (status 00).
        private void closeFiles() {
            dalytranFile = null;
            xrefFile = null;
            accountFile = null;
        }

        // Z-ABEND-PROGRAM
        private void zAbendProgram() {
            display("ABENDING PROGRAM");
            throw new AbendException(ABEND_CODE, "CEE3ABD called by CBTRN01C");
        }

        // Z-DISPLAY-IO-STATUS
        private void zDisplayIoStatus(String ioStatus) {
            display("FILE STATUS IS: NNNN" + ioStatus04(ioStatus));
        }

        private void writeVerification(boolean atEof, Integer acctStatus, String status) {
            lookups++;
            if (atEof) {
                eofReprocessed++;
            }
            boolean xrefFound = wsXrefReadStatus == 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lookup_seq", lookups);
            row.put("dalytran_record_no", dalytranRecordNo == 0 ? null : dalytranRecordNo);
            row.put("eof_reprocessed", atEof);
            row.put("dalytran_id", SysoutDataset.rtrim(dalytranField("DALYTRAN-ID")));
            row.put("dalytran_card_num", SysoutDataset.rtrim(dalytranField("DALYTRAN-CARD-NUM")));
            row.put("xref_read_status", wsXrefReadStatus);
            row.put("xref_acct_id", xrefFound ? cardXrefRecord.getString("XREF-ACCT-ID") : null);
            row.put("xref_cust_id", xrefFound ? cardXrefRecord.getString("XREF-CUST-ID") : null);
            row.put("acct_read_status", acctStatus);
            row.put("verification_status", status);
            verification.write(row);
        }

        /** Alphanumeric field of DALYTRAN-RECORD, sliced from the record image (no numeric decoding). */
        private String dalytranField(String name) {
            int off = DALYTRAN_RECORD.offset(name);
            return dalytranRecord.substring(off, off + DALYTRAN_RECORD.field(name).length());
        }

        private void display(String line) {
            sysout.display(line);
        }
    }

    /**
     * IO-STATUS-04 as built by Z-DISPLAY-IO-STATUS: {@code 00nn} for a numeric status, otherwise
     * status-1 followed by the 3-digit binary value of status-2 (code point of the character).
     */
    static String ioStatus04(String ioStatus) {
        boolean numeric = ioStatus.chars().allMatch(c -> c >= '0' && c <= '9');
        if (!numeric || ioStatus.charAt(0) == '9') {
            return ioStatus.charAt(0) + String.format("%03d", ioStatus.charAt(1) & 0xFF);
        }
        return "00" + ioStatus;
    }
}
