package com.carddemo.batch.cbact01c;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobResult;
import com.carddemo.batch.common.output.DatasetWriter;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.record.CobolRecord;
import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.record.KeyedFile;
import com.carddemo.batch.common.record.RecordLayout;
import com.carddemo.batch.common.record.ZonedDecimal;
import com.carddemo.batch.common.schema.DatasetSchema;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.carddemo.batch.common.record.FieldSpec.alnum;
import static com.carddemo.batch.common.record.FieldSpec.code;

/**
 * CBACT01C (JCL READACCT, STEP05): browses the account KSDS (DD ACCTFILE, CVACT01Y) in key
 * order, DISPLAYs every record and writes three reformatted files: OUTFILE, ARRYFILE and
 * VBRCFILE, published as the {@code account_out}, {@code account_array} and {@code account_vbrc}
 * Parquet datasets, plus the DISPLAY lines as the {@code sysout} CSV.
 * See {@code docs/modernization/business-rules/CBACT01C.md}.
 */
public final class Cbact01cJob implements BatchJob {
    public static final String PROGRAM_ID = "CBACT01C";
    public static final String ACCTFILE = "ACCTFILE";
    public static final int ABEND_CODE = 999;

    public static final String READ = "acctfile_records_read";
    public static final String OUT_WRITTEN = "outfile_records_written";
    public static final String ARRY_WRITTEN = "arryfile_records_written";
    public static final String VBRC_WRITTEN = "vbrcfile_records_written";

    /** FD-ACCTFILE-REC: the KSDS record as the FD sees it (key + opaque data). */
    static final RecordLayout FD_ACCTFILE_REC = new RecordLayout("FD-ACCTFILE-REC", "CBACT01C",
            List.of(code("FD-ACCT-ID", 11), alnum("FD-ACCT-DATA", 289)));

    static final BigDecimal ZERO = new BigDecimal("0.00");
    static final BigDecimal DEFAULT_CYC_DEBIT = new BigDecimal("2525.00");
    static final BigDecimal ARR_CYC_DEBIT_1 = new BigDecimal("1005.00");
    static final BigDecimal ARR_CYC_DEBIT_2 = new BigDecimal("1525.00");
    static final BigDecimal ARR_CURR_BAL_3 = new BigDecimal("-1025.00");
    static final BigDecimal ARR_CYC_DEBIT_3 = new BigDecimal("-2500.00");
    static final int VB1_LENGTH = 12;
    static final int VB2_LENGTH = 39;

    /** File status for an I/O failure that has no VSAM/QSAM equivalent (permanent error). */
    static final String STATUS_IO_ERROR = "30";
    static final String STATUS_FILE_NOT_FOUND = "35";

    @Override
    public String programId() {
        return PROGRAM_ID;
    }

    @Override
    public JobResult run(JobContext ctx) {
        return new Execution(ctx).procedureDivision();
    }

    /** 9910-DISPLAY-IO-STATUS: the 4-character IO-STATUS-04 shown after "FILE STATUS IS: NNNN". */
    static String ioStatus04(String ioStatus) {
        char stat1 = ioStatus.charAt(0);
        char stat2 = ioStatus.charAt(1);
        boolean numeric = Character.isDigit(stat1) && Character.isDigit(stat2);
        if (!numeric || stat1 == '9') {
            // TWO-BYTES-BINARY gets IO-STAT2 in its low-order byte; shown as PIC 999.
            return stat1 + String.format("%03d", (stat2 & 0xFF) % 1000);
        }
        return "00" + ioStatus;
    }

    static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }

    /** One run: WORKING-STORAGE and FD record areas live here. */
    private static final class Execution {
        private final JobContext ctx;
        private final Map<String, Long> counters = new LinkedHashMap<>();
        private final List<DatasetWriter> opened = new ArrayList<>();
        private DatasetWriter sysout;
        private long sysoutLines;

        private Iterator<CobolRecord> acctfile;
        private DatasetWriter outFile;
        private DatasetWriter arryFile;
        private DatasetWriter vbrcFile;

        // WORKING-STORAGE
        private CobolRecord accountRecord;
        private String accountRecordImage;
        private final Codatecn codatecn = new Codatecn();
        private String wsReissueDate = " ".repeat(10);
        private boolean endOfFile;
        private int applResult;
        private String ioStatus = "00";

        // FD OUT-FILE record area. Only OUT-ACCT-CURR-CYC-DEBIT can survive from one record to the
        // next (BR-CBACT01C-07); it starts as low-values, which read back as zero.
        private BigDecimal outAcctCurrCycDebit = ZERO;
        private final Map<String, Object> outAcctRec = new LinkedHashMap<>();
        // FD ARRY-FILE record area
        private final Map<String, Object> arrArrayRec = new LinkedHashMap<>();
        // VBRC-REC1 / VBRC-REC2
        private String vb1AcctId;
        private String vb1AcctActiveStatus;
        private String vb2AcctId;
        private BigDecimal vb2AcctCurrBal;
        private BigDecimal vb2AcctCreditLimit;
        private String vb2AcctReissueYyyy;

        Execution(JobContext ctx) {
            this.ctx = ctx;
            for (String c : List.of(READ, OUT_WRITTEN, ARRY_WRITTEN, VBRC_WRITTEN)) {
                counters.put(c, 0L);
            }
        }

        // PROCEDURE DIVISION
        JobResult procedureDivision() {
            sysout = open(Cbact01cSchemas.SYSOUT, OutputFormat.CSV);
            boolean ok = false;
            try {
                display("START OF EXECUTION OF PROGRAM CBACT01C");
                acctfileOpen();
                outfileOpen();
                arrfileOpen();
                vbrfileOpen();

                while (!endOfFile) {
                    acctfileGetNext();
                    if (!endOfFile) {
                        display(accountRecordImage);
                    }
                }

                acctfileClose();
                display("END OF EXECUTION OF PROGRAM CBACT01C");
                ok = true;
                return new JobResult(0, Map.copyOf(counters));
            } finally {
                if (!ok) {
                    // Abend: close what is open so the records written so far are kept, as the
                    // system closes the DCBs of an abending step. No manifest is written.
                    for (DatasetWriter w : opened) {
                        w.close();
                    }
                }
            }
        }

        // 1000-ACCTFILE-GET-NEXT
        private void acctfileGetNext() {
            String status;
            if (!acctfile.hasNext()) {
                status = "10";
            } else {
                String image = acctfile.next().toFixedWidth();
                try {
                    // READ ACCTFILE-FILE INTO ACCOUNT-RECORD
                    accountRecord = Copybooks.ACCOUNT_RECORD.parse(image);
                    accountRecordImage = image;
                    status = "00";
                } catch (IllegalArgumentException e) {
                    status = STATUS_IO_ERROR;
                }
            }
            if ("00".equals(status)) {
                applResult = 0;
                counters.merge(READ, 1L, Long::sum);
                initializeArrArrayRec();
                displayAcctRecord();
                populAcctRecord();
                writeAcctRecord();
                populArrayRecord();
                writeArryRecord();
                vb1AcctId = "00000000000";
                vb1AcctActiveStatus = " ";
                populVbrcRecord();
                writeVb1Record();
                writeVb2Record();
            } else if ("10".equals(status)) {
                applResult = 16;
            } else {
                applResult = 12;
            }
            if (applResult == 0) {
                return;
            }
            if (applResult == 16) {
                endOfFile = true;
            } else {
                display("ERROR READING ACCOUNT FILE");
                ioStatus = status;
                displayIoStatus();
                abendProgram();
            }
        }

        // 1100-DISPLAY-ACCT-RECORD: numeric items are DISPLAYed as stored (zoned with overpunch).
        private void displayAcctRecord() {
            display("ACCT-ID                 :" + raw("ACCT-ID"));
            display("ACCT-ACTIVE-STATUS      :" + raw("ACCT-ACTIVE-STATUS"));
            display("ACCT-CURR-BAL           :" + raw("ACCT-CURR-BAL"));
            display("ACCT-CREDIT-LIMIT       :" + raw("ACCT-CREDIT-LIMIT"));
            display("ACCT-CASH-CREDIT-LIMIT  :" + raw("ACCT-CASH-CREDIT-LIMIT"));
            display("ACCT-OPEN-DATE          :" + raw("ACCT-OPEN-DATE"));
            display("ACCT-EXPIRAION-DATE     :" + raw("ACCT-EXPIRAION-DATE"));
            display("ACCT-REISSUE-DATE       :" + raw("ACCT-REISSUE-DATE"));
            display("ACCT-CURR-CYC-CREDIT    :" + raw("ACCT-CURR-CYC-CREDIT"));
            display("ACCT-CURR-CYC-DEBIT     :" + raw("ACCT-CURR-CYC-DEBIT"));
            display("ACCT-GROUP-ID           :" + raw("ACCT-GROUP-ID"));
            display("-------------------------------------------------");
        }

        // 1300-POPUL-ACCT-RECORD
        private void populAcctRecord() {
            outAcctRec.put("out_acct_id", accountRecord.getString("ACCT-ID"));
            outAcctRec.put("out_acct_active_status", accountRecord.getString("ACCT-ACTIVE-STATUS"));
            outAcctRec.put("out_acct_curr_bal", accountRecord.getDecimal("ACCT-CURR-BAL"));
            outAcctRec.put("out_acct_credit_limit", accountRecord.getDecimal("ACCT-CREDIT-LIMIT"));
            outAcctRec.put("out_acct_cash_credit_limit", accountRecord.getDecimal("ACCT-CASH-CREDIT-LIMIT"));
            outAcctRec.put("out_acct_open_date", accountRecord.getString("ACCT-OPEN-DATE"));
            outAcctRec.put("out_acct_expiraion_date", accountRecord.getString("ACCT-EXPIRAION-DATE"));
            String reissue = accountRecord.getString("ACCT-REISSUE-DATE");
            codatecn.moveToInpDate(reissue);
            wsReissueDate = reissue;
            codatecn.type = '2';
            codatecn.outType = '2';
            Cobdatft.call(codatecn);
            outAcctRec.put("out_acct_reissue_date", codatecn.outDate.substring(0, 10));
            outAcctRec.put("out_acct_curr_cyc_credit", accountRecord.getDecimal("ACCT-CURR-CYC-CREDIT"));
            if (accountRecord.getDecimal("ACCT-CURR-CYC-DEBIT").signum() == 0) {
                outAcctCurrCycDebit = DEFAULT_CYC_DEBIT;
            }
            outAcctRec.put("out_acct_curr_cyc_debit", outAcctCurrCycDebit);
            outAcctRec.put("out_acct_group_id", accountRecord.getString("ACCT-GROUP-ID"));
        }

        // 1350-WRITE-ACCT-RECORD
        private void writeAcctRecord() {
            String status = write(outFile, outAcctRec);
            if (!"00".equals(status) && !"10".equals(status)) {
                display("ACCOUNT FILE WRITE STATUS IS:" + status);
                ioStatus = status;
                displayIoStatus();
                abendProgram();
            }
            counters.merge(OUT_WRITTEN, 1L, Long::sum);
        }

        // INITIALIZE ARR-ARRAY-REC
        private void initializeArrArrayRec() {
            arrArrayRec.put("arr_acct_id", "00000000000");
            for (int i = 1; i <= 5; i++) {
                arrArrayRec.put("arr_acct_curr_bal_" + i, ZERO);
                arrArrayRec.put("arr_acct_curr_cyc_debit_" + i, ZERO);
            }
        }

        // 1400-POPUL-ARRAY-RECORD
        private void populArrayRecord() {
            BigDecimal bal = accountRecord.getDecimal("ACCT-CURR-BAL");
            arrArrayRec.put("arr_acct_id", accountRecord.getString("ACCT-ID"));
            arrArrayRec.put("arr_acct_curr_bal_1", bal);
            arrArrayRec.put("arr_acct_curr_cyc_debit_1", ARR_CYC_DEBIT_1);
            arrArrayRec.put("arr_acct_curr_bal_2", bal);
            arrArrayRec.put("arr_acct_curr_cyc_debit_2", ARR_CYC_DEBIT_2);
            arrArrayRec.put("arr_acct_curr_bal_3", ARR_CURR_BAL_3);
            arrArrayRec.put("arr_acct_curr_cyc_debit_3", ARR_CYC_DEBIT_3);
        }

        // 1450-WRITE-ARRY-RECORD
        private void writeArryRecord() {
            String status = write(arryFile, arrArrayRec);
            if (!"00".equals(status) && !"10".equals(status)) {
                display("ACCOUNT FILE WRITE STATUS IS:" + status);
                ioStatus = status;
                displayIoStatus();
                abendProgram();
            }
            counters.merge(ARRY_WRITTEN, 1L, Long::sum);
        }

        // 1500-POPUL-VBRC-RECORD
        private void populVbrcRecord() {
            vb1AcctId = accountRecord.getString("ACCT-ID");
            vb2AcctId = vb1AcctId;
            vb1AcctActiveStatus = accountRecord.getString("ACCT-ACTIVE-STATUS");
            vb2AcctCurrBal = accountRecord.getDecimal("ACCT-CURR-BAL");
            vb2AcctCreditLimit = accountRecord.getDecimal("ACCT-CREDIT-LIMIT");
            vb2AcctReissueYyyy = wsReissueDate.substring(0, 4);
            display("VBRC-REC1:" + vb1AcctId + vb1AcctActiveStatus);
            display("VBRC-REC2:" + vb2AcctId + zoned(vb2AcctCurrBal) + zoned(vb2AcctCreditLimit) + vb2AcctReissueYyyy);
        }

        // 1550-WRITE-VB1-RECORD
        private void writeVb1Record() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vbrc_record_type", 1);
            row.put("vbrc_record_length", VB1_LENGTH);
            row.put("vbrc_acct_id", vb1AcctId);
            row.put("vb1_acct_active_status", vb1AcctActiveStatus);
            row.put("vb2_acct_curr_bal", null);
            row.put("vb2_acct_credit_limit", null);
            row.put("vb2_acct_reissue_yyyy", null);
            writeVbrc(row);
        }

        // 1575-WRITE-VB2-RECORD
        private void writeVb2Record() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vbrc_record_type", 2);
            row.put("vbrc_record_length", VB2_LENGTH);
            row.put("vbrc_acct_id", vb2AcctId);
            row.put("vb1_acct_active_status", null);
            row.put("vb2_acct_curr_bal", vb2AcctCurrBal);
            row.put("vb2_acct_credit_limit", vb2AcctCreditLimit);
            row.put("vb2_acct_reissue_yyyy", vb2AcctReissueYyyy);
            writeVbrc(row);
        }

        private void writeVbrc(Map<String, Object> row) {
            String status = write(vbrcFile, row);
            if (!"00".equals(status) && !"10".equals(status)) {
                display("ACCOUNT FILE WRITE STATUS IS:" + status);
                ioStatus = status;
                displayIoStatus();
                abendProgram();
            }
            counters.merge(VBRC_WRITTEN, 1L, Long::sum);
        }

        // 0000-ACCTFILE-OPEN: OPEN INPUT of the KSDS loads the unload image in key order.
        private void acctfileOpen() {
            applResult = 8;
            String status;
            Path file = ctx.input(ACCTFILE);
            if (!Files.isRegularFile(file)) {
                status = STATUS_FILE_NOT_FOUND;
            } else {
                try {
                    acctfile = KeyedFile.load(file, FD_ACCTFILE_REC, "FD-ACCT-ID").inKeyOrder().iterator();
                    status = "00";
                } catch (IllegalArgumentException | UncheckedIOException e) {
                    status = STATUS_IO_ERROR;
                }
            }
            applResult = "00".equals(status) ? 0 : 12;
            if (applResult != 0) {
                display("ERROR OPENING ACCTFILE");
                ioStatus = status;
                displayIoStatus();
                abendProgram();
            }
        }

        // 2000-OUTFILE-OPEN
        private void outfileOpen() {
            outFile = openOutput(Cbact01cSchemas.ACCOUNT_OUT, "ERROR OPENING OUTFILE");
        }

        // 3000-ARRFILE-OPEN
        private void arrfileOpen() {
            arryFile = openOutput(Cbact01cSchemas.ACCOUNT_ARRAY, "ERROR OPENING ARRAYFILE");
        }

        // 4000-VBRFILE-OPEN
        private void vbrfileOpen() {
            vbrcFile = openOutput(Cbact01cSchemas.ACCOUNT_VBRC, "ERROR OPENING VBRC FILE");
        }

        private DatasetWriter openOutput(DatasetSchema schema, String errorText) {
            applResult = 8;
            DatasetWriter w = null;
            String status;
            try {
                w = open(schema, OutputFormat.PARQUET);
                status = "00";
            } catch (UncheckedIOException e) {
                status = STATUS_IO_ERROR;
            }
            applResult = "00".equals(status) ? 0 : 12;
            if (applResult != 0) {
                display(errorText + status);
                ioStatus = status;
                displayIoStatus();
                abendProgram();
            }
            return w;
        }

        // 9000-ACCTFILE-CLOSE: closing the in-memory KSDS image cannot fail.
        private void acctfileClose() {
            applResult = 8;
            acctfile = null;
            applResult = 0;
        }

        // 9999-ABEND-PROGRAM
        private void abendProgram() {
            display("ABENDING PROGRAM");
            throw new AbendException(ABEND_CODE, PROGRAM_ID + ": CEE3ABD ABCODE=" + ABEND_CODE + " TIMING=0 (file status " + ioStatus + ")");
        }

        // 9910-DISPLAY-IO-STATUS
        private void displayIoStatus() {
            display("FILE STATUS IS: NNNN" + ioStatus04(ioStatus));
        }

        private String raw(String field) {
            int offset = Copybooks.ACCOUNT_RECORD.offset(field);
            return accountRecordImage.substring(offset, offset + Copybooks.ACCOUNT_RECORD.field(field).length());
        }

        private static String zoned(BigDecimal v) {
            return ZonedDecimal.encode(v, 10, 2, true);
        }

        private DatasetWriter open(DatasetSchema schema, OutputFormat format) {
            DatasetWriter w = ctx.openDataset(schema.name(), schema, format);
            opened.add(w);
            return w;
        }

        /** WRITE with file status: strings are written right-trimmed (canonical form). */
        private static String write(DatasetWriter w, Map<String, Object> rec) {
            Map<String, Object> row = new LinkedHashMap<>();
            rec.forEach((k, v) -> row.put(k, v instanceof String s ? rtrim(s) : v));
            try {
                w.write(row);
                return "00";
            } catch (UncheckedIOException e) {
                return STATUS_IO_ERROR;
            }
        }

        private void display(String line) {
            ctx.sysout().println(line);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("line_no", (int) ++sysoutLines);
            row.put("text", rtrim(line));
            sysout.write(row);
        }
    }
}
