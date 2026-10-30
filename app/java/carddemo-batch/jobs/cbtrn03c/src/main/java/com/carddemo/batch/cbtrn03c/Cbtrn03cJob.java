package com.carddemo.batch.cbtrn03c;

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
import com.carddemo.batch.common.record.ZonedDecimal;
import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.ColumnType;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CBTRN03C: transaction detail report (JCL TRANREPT). The REPRO + SORT/INCLUDE step is folded in as
 * {@link TransactSortStep}. One method per COBOL paragraph; see docs/modernization/business-rules/CBTRN03C.md.
 *
 * <p>Inputs: TRANFILE (TRANSACT KSDS unload, CVTRA05Y), CARDXREF, TRANTYPE, TRANCATG, DATEPARM. When the
 * DATEPARM DD is absent, {@code --param START-DATE=... --param END-DATE=...} supply the record. The SORT
 * INCLUDE dates are {@code SORT-START-DATE}/{@code SORT-END-DATE}, defaulting to the DATEPARM dates (else
 * the TRANREPT SYMNAMES values 2022-01-01/2022-07-06).
 */
public final class Cbtrn03cJob implements BatchJob {

    public static final String PROGRAM_ID = "CBTRN03C";
    public static final String DATASET = "transaction_report";
    public static final int ABEND_CODE = 999;
    static final String DEFAULT_START_DATE = "2022-01-01";
    static final String DEFAULT_END_DATE = "2022-07-06";
    static final int PAGE_SIZE = 20;

    /** FD-DATEPARM-REC (80) as read INTO WS-DATEPARM-RECORD. */
    static final RecordLayout DATEPARM_RECORD = new RecordLayout("WS-DATEPARM-RECORD", "CBTRN03C", List.of(
            FieldSpec.alnum("WS-START-DATE", 10), FieldSpec.filler(1), FieldSpec.alnum("WS-END-DATE", 10),
            FieldSpec.filler(59)));

    public static final DatasetSchema SCHEMA = new DatasetSchema(DATASET,
            "CBTRN03C TRANREPT print file, one row per 133-byte report line (CVTRA07Y layouts)", List.of(
            Column.of("line_no", ColumnType.INT, "1-based record number in TRANREPT"),
            Column.of("page_no", ColumnType.INT, "Page number; a page starts at each REPORT-NAME-HEADER"),
            Column.of("line_type", ColumnType.STRING,
                    "HEADER, BLANK, COLUMN_HEADER, RULE, DETAIL, PAGE_TOTAL, ACCOUNT_TOTAL or GRAND_TOTAL"),
            Column.nullable("tran_id", ColumnType.STRING, "TRAN-REPORT-TRANS-ID X(16)"),
            Column.nullable("account_id", ColumnType.STRING, "TRAN-REPORT-ACCOUNT-ID X(11) from XREF-ACCT-ID"),
            Column.nullable("tran_type_cd", ColumnType.STRING, "TRAN-REPORT-TYPE-CD X(02)"),
            Column.nullable("tran_type_desc", ColumnType.STRING, "TRAN-REPORT-TYPE-DESC X(15), truncated TRAN-TYPE-DESC"),
            Column.nullable("tran_cat_cd", ColumnType.STRING, "TRAN-REPORT-CAT-CD 9(04)"),
            Column.nullable("tran_cat_desc", ColumnType.STRING, "TRAN-REPORT-CAT-DESC X(29), truncated TRAN-CAT-TYPE-DESC"),
            Column.nullable("tran_source", ColumnType.STRING, "TRAN-REPORT-SOURCE X(10)"),
            Column.nullable("amount", ColumnType.decimal(11, 2),
                    "DETAIL: TRAN-AMT; *_TOTAL: the printed S9(09)V99 total; null for other lines"),
            Column.of("line_text", ColumnType.STRING, "The exact 133-byte line, right-trimmed")));

    @Override
    public String programId() {
        return PROGRAM_ID;
    }

    @Override
    public JobResult run(JobContext ctx) {
        return new Run(ctx).procedureDivision();
    }

    /** WORKING-STORAGE plus the PROCEDURE DIVISION for one execution. */
    private static final class Run {
        private final JobContext ctx;
        private final PrintStream sysout;

        private Iterator<CobolRecord> tranFile;
        private DatasetWriter reportFile;
        private KeyedFile xrefFile;
        private KeyedFile tranTypeFile;
        private KeyedFile tranCatgFile;
        private List<CobolRecord> dateParmFile;

        private CobolRecord tranRecord = Copybooks.TRAN_RECORD.newRecord();
        private CobolRecord cardXrefRecord = Copybooks.CARD_XREF_RECORD.newRecord();
        private CobolRecord tranTypeRecord = Copybooks.TRAN_TYPE_RECORD.newRecord();
        private CobolRecord tranCatRecord = Copybooks.TRAN_CAT_RECORD.newRecord();
        private String wsStartDate = " ".repeat(10);
        private String wsEndDate = " ".repeat(10);
        private boolean endOfFile;
        private boolean firstTime = true;
        private long lineCounter;
        private BigDecimal pageTotal = BigDecimal.ZERO.setScale(2);
        private BigDecimal accountTotal = BigDecimal.ZERO.setScale(2);
        private BigDecimal grandTotal = BigDecimal.ZERO.setScale(2);
        private String currCardNum = " ".repeat(16);

        private final Map<String, Long> counters = new LinkedHashMap<>();
        private int pageNo;
        private int lineNo;

        Run(JobContext ctx) {
            this.ctx = ctx;
            this.sysout = ctx.sysout();
            for (String c : List.of("transact_unloaded", "sort_included", "sort_omitted", "tranfile_read",
                    "detail_lines", "account_totals", "page_totals", "pages", "report_lines")) {
                counters.put(c, 0L);
            }
        }

        // PROCEDURE DIVISION
        JobResult procedureDivision() {
            display("START OF EXECUTION OF PROGRAM CBTRN03C");
            tranfileOpen();
            reptfileOpen();
            cardxrefOpen();
            trantypeOpen();
            trancatgOpen();
            dateparmOpen();

            dateparmRead();

            while (!endOfFile) {
                tranfileGetNext();
                String procDate = tranRecord.getString("TRAN-PROC-TS").substring(0, 10);
                if (!(procDate.compareTo(wsStartDate) >= 0 && procDate.compareTo(wsEndDate) <= 0)) {
                    // NEXT SENTENCE: control leaves the PERFORM UNTIL; no totals are written.
                    break;
                }
                if (!endOfFile) {
                    display(Copybooks.TRAN_RECORD.format(tranRecord));
                    String cardNum = tranRecord.getString("TRAN-CARD-NUM");
                    if (!currCardNum.equals(cardNum)) {
                        if (!firstTime) {
                            writeAccountTotals();
                        }
                        currCardNum = cardNum;
                        lookupXref(cardNum);
                    }
                    lookupTrantype(tranRecord.getString("TRAN-TYPE-CD"));
                    lookupTrancatg(tranRecord.getString("TRAN-TYPE-CD"), tranRecord.getString("TRAN-CAT-CD"));
                    writeTransactionReport();
                } else {
                    BigDecimal tranAmt = tranRecord.getDecimal("TRAN-AMT");
                    display("TRAN-AMT " + zoned(tranAmt));
                    display("WS-PAGE-TOTAL" + zoned(pageTotal));
                    pageTotal = ReportLines.toPicture(pageTotal.add(tranAmt));
                    accountTotal = ReportLines.toPicture(accountTotal.add(tranAmt));
                    writePageTotals();
                    writeGrandTotals();
                }
            }
            reportFile.close();
            display("END OF EXECUTION OF PROGRAM CBTRN03C");
            return JobResult.ok(counters);
        }

        // 0550-DATEPARM-READ
        void dateparmRead() {
            if (dateParmFile.isEmpty()) {
                endOfFile = true;
                return;
            }
            CobolRecord parm = dateParmFile.get(0);
            wsStartDate = parm.getString("WS-START-DATE");
            wsEndDate = parm.getString("WS-END-DATE");
            display("Reporting from " + wsStartDate + " to " + wsEndDate);
        }

        // 1000-TRANFILE-GET-NEXT
        void tranfileGetNext() {
            if (tranFile.hasNext()) {
                tranRecord = tranFile.next();
                count("tranfile_read");
            } else {
                endOfFile = true;
            }
        }

        // 1100-WRITE-TRANSACTION-REPORT
        void writeTransactionReport() {
            if (firstTime) {
                firstTime = false;
                writeHeaders();
            }
            if (lineCounter % PAGE_SIZE == 0) {
                writePageTotals();
                writeHeaders();
            }
            BigDecimal tranAmt = tranRecord.getDecimal("TRAN-AMT");
            pageTotal = ReportLines.toPicture(pageTotal.add(tranAmt));
            accountTotal = ReportLines.toPicture(accountTotal.add(tranAmt));
            writeDetail();
        }

        // 1110-WRITE-PAGE-TOTALS
        void writePageTotals() {
            writeReportRec(ReportLines.pageTotal(pageTotal), "PAGE_TOTAL", ReportLines.toPicture(pageTotal), null);
            count("page_totals");
            grandTotal = ReportLines.toPicture(grandTotal.add(pageTotal));
            pageTotal = BigDecimal.ZERO.setScale(2);
            lineCounter++;
            writeReportRec(ReportLines.HEADER_2, "RULE", null, null);
            lineCounter++;
        }

        // 1120-WRITE-ACCOUNT-TOTALS
        void writeAccountTotals() {
            writeReportRec(ReportLines.accountTotal(accountTotal), "ACCOUNT_TOTAL", accountTotal, null);
            count("account_totals");
            accountTotal = BigDecimal.ZERO.setScale(2);
            lineCounter++;
            writeReportRec(ReportLines.HEADER_2, "RULE", null, null);
            lineCounter++;
        }

        // 1110-WRITE-GRAND-TOTALS
        void writeGrandTotals() {
            writeReportRec(ReportLines.grandTotal(grandTotal), "GRAND_TOTAL", grandTotal, null);
        }

        // 1120-WRITE-HEADERS
        void writeHeaders() {
            pageNo++;
            count("pages");
            writeReportRec(ReportLines.nameHeader(wsStartDate, wsEndDate), "HEADER", null, null);
            lineCounter++;
            writeReportRec(ReportLines.BLANK_LINE, "BLANK", null, null);
            lineCounter++;
            writeReportRec(ReportLines.HEADER_1, "COLUMN_HEADER", null, null);
            lineCounter++;
            writeReportRec(ReportLines.HEADER_2, "RULE", null, null);
            lineCounter++;
        }

        // 1120-WRITE-DETAIL
        void writeDetail() {
            ReportLines.Detail d = new ReportLines.Detail(
                    tranRecord.getString("TRAN-ID"),
                    cardXrefRecord.getString("XREF-ACCT-ID"),
                    tranRecord.getString("TRAN-TYPE-CD"),
                    ReportLines.pad(tranTypeRecord.getString("TRAN-TYPE-DESC"), 15),
                    tranRecord.getString("TRAN-CAT-CD"),
                    ReportLines.pad(tranCatRecord.getString("TRAN-CAT-TYPE-DESC"), 29),
                    tranRecord.getString("TRAN-SOURCE"),
                    tranRecord.getDecimal("TRAN-AMT"));
            writeReportRec(ReportLines.detail(d), "DETAIL", d.amount(), d);
            count("detail_lines");
            lineCounter++;
        }

        // 1111-WRITE-REPORT-REC
        void writeReportRec(String line, String lineType, BigDecimal amount, ReportLines.Detail d) {
            String image = ReportLines.toRecord(line);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("line_no", ++lineNo);
            row.put("page_no", pageNo);
            row.put("line_type", lineType);
            row.put("tran_id", d == null ? null : rtrim(d.tranId()));
            row.put("account_id", d == null ? null : rtrim(d.accountId()));
            row.put("tran_type_cd", d == null ? null : rtrim(d.typeCd()));
            row.put("tran_type_desc", d == null ? null : rtrim(d.typeDesc()));
            row.put("tran_cat_cd", d == null ? null : rtrim(d.catCd()));
            row.put("tran_cat_desc", d == null ? null : rtrim(d.catDesc()));
            row.put("tran_source", d == null ? null : rtrim(d.source()));
            row.put("amount", amount);
            row.put("line_text", rtrim(image));
            reportFile.write(row);
            count("report_lines");
        }

        // 0000-TRANFILE-OPEN (preceded by the folded-in STEP05R REPRO + SORT)
        void tranfileOpen() {
            Optional<Path> unload = ctx.optionalInput("TRANFILE");
            if (unload.isEmpty()) {
                openError("ERROR OPENING TRANFILE");
            }
            String[] sortDates = sortDates();
            TransactSortStep.Result sorted = TransactSortStep.run(unload.get(), sortDates[0], sortDates[1]);
            counters.put("transact_unloaded", (long) sorted.unloaded().size());
            counters.put("sort_included", (long) sorted.selected().size());
            counters.put("sort_omitted", sorted.omitted());
            tranFile = sorted.selected().iterator();
        }

        // 0100-REPTFILE-OPEN
        void reptfileOpen() {
            reportFile = ctx.openDataset(DATASET, SCHEMA, OutputFormat.CSV);
        }

        // 0200-CARDXREF-OPEN
        void cardxrefOpen() {
            xrefFile = openKsds("CARDXREF", Copybooks.CARD_XREF_RECORD, "ERROR OPENING CROSS REF FILE", "XREF-CARD-NUM");
        }

        // 0300-TRANTYPE-OPEN
        void trantypeOpen() {
            tranTypeFile = openKsds("TRANTYPE", Copybooks.TRAN_TYPE_RECORD, "ERROR OPENING TRANSACTION TYPE FILE", "TRAN-TYPE");
        }

        // 0400-TRANCATG-OPEN
        void trancatgOpen() {
            tranCatgFile = openKsds("TRANCATG", Copybooks.TRAN_CAT_RECORD, "ERROR OPENING TRANSACTION CATG FILE",
                    "TRAN-TYPE-CD", "TRAN-CAT-CD");
        }

        // 0500-DATEPARM-OPEN
        void dateparmOpen() {
            dateParmFile = dateParmRecords().orElseGet(() -> {
                openError("ERROR OPENING DATE PARM FILE");
                return null;
            });
        }

        // 1500-A-LOOKUP-XREF
        void lookupXref(String cardNum) {
            cardXrefRecord = xrefFile.read(cardNum).orElseGet(() -> invalidKey("INVALID CARD NUMBER : " + cardNum));
        }

        // 1500-B-LOOKUP-TRANTYPE
        void lookupTrantype(String typeCd) {
            tranTypeRecord = tranTypeFile.read(typeCd).orElseGet(() -> invalidKey("INVALID TRANSACTION TYPE : " + typeCd));
        }

        // 1500-C-LOOKUP-TRANCATG
        void lookupTrancatg(String typeCd, String catCd) {
            String key = typeCd + catCd;
            tranCatRecord = tranCatgFile.read(key).orElseGet(() -> invalidKey("INVALID TRAN CATG KEY : " + key));
        }

        private CobolRecord invalidKey(String message) {
            display(message);
            displayIoStatus("23");
            throw abendProgram(message);
        }

        private KeyedFile openKsds(String dd, RecordLayout layout, String error, String... keyFields) {
            Optional<Path> file = ctx.optionalInput(dd);
            if (file.isEmpty()) {
                openError(error);
            }
            return KeyedFile.load(file.get(), layout, keyFields);
        }

        /** OPEN of an unallocated DD: file status 35. */
        private void openError(String message) {
            display(message);
            displayIoStatus("35");
            throw abendProgram(message);
        }

        private Optional<List<CobolRecord>> dateParmRecords() {
            Optional<Path> dd = ctx.optionalInput("DATEPARM");
            if (dd.isPresent()) {
                return Optional.of(FixedWidthFile.read(dd.get(), DATEPARM_RECORD));
            }
            Optional<String> start = ctx.param("START-DATE");
            Optional<String> end = ctx.param("END-DATE");
            if (start.isPresent() && end.isPresent()) {
                CobolRecord rec = DATEPARM_RECORD.newRecord();
                rec.set("WS-START-DATE", start.get());
                rec.set("WS-END-DATE", end.get());
                return Optional.of(List.of(rec));
            }
            return Optional.empty();
        }

        /** PARM-START-DATE / PARM-END-DATE for the SORT INCLUDE. */
        private String[] sortDates() {
            List<CobolRecord> parm = dateParmRecords().orElse(List.of());
            String start = parm.isEmpty() ? DEFAULT_START_DATE : parm.get(0).getString("WS-START-DATE");
            String end = parm.isEmpty() ? DEFAULT_END_DATE : parm.get(0).getString("WS-END-DATE");
            return new String[] {ctx.param("SORT-START-DATE").orElse(start), ctx.param("SORT-END-DATE").orElse(end)};
        }

        // 9910-DISPLAY-IO-STATUS
        void displayIoStatus(String ioStatus) {
            boolean numeric = ioStatus.chars().allMatch(Character::isDigit);
            if (!numeric || ioStatus.charAt(0) == '9') {
                display("FILE STATUS IS: NNNN" + ioStatus.charAt(0) + String.format("%03d", (int) ioStatus.charAt(1)));
            } else {
                display("FILE STATUS IS: NNNN00" + ioStatus);
            }
        }

        // 9999-ABEND-PROGRAM
        AbendException abendProgram(String reason) {
            display("ABENDING PROGRAM");
            return new AbendException(ABEND_CODE, reason);
        }

        private void display(String line) {
            sysout.println(line);
        }

        private void count(String counter) {
            counters.merge(counter, 1L, Long::sum);
        }

        private static String zoned(BigDecimal v) {
            return ZonedDecimal.encode(v, 9, 2, true);
        }

        private static String rtrim(String s) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == ' ') {
                end--;
            }
            return s.substring(0, end);
        }
    }
}
