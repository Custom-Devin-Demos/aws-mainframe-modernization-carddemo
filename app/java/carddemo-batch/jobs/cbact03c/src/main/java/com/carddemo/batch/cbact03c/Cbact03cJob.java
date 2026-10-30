package com.carddemo.batch.cbact03c;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobResult;
import com.carddemo.batch.common.output.DatasetWriter;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.record.CobolRecord;
import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.ColumnType;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * CBACT03C (job READXREF, step STEP05): reads the card cross-reference KSDS (DD XREFFILE, CVACT03Y)
 * sequentially in key order and DISPLAYs every record. Business rules: docs/modernization/business-rules/CBACT03C.md.
 *
 * <p>Outputs: {@code xref_listing} (Parquet, one row per record read, key order) and {@code sysout} (CSV, every
 * DISPLAY line). RETURN-CODE is always 0; every file-status error ends in {@code CEE3ABD} (abend 999).
 */
public final class Cbact03cJob implements BatchJob {

    public static final String PROGRAM_ID = "CBACT03C";
    public static final String XREFFILE_DD = "XREFFILE";
    public static final String XREF_LISTING = "xref_listing";
    public static final String SYSOUT = "sysout";
    public static final int ABEND_CODE = 999;
    public static final String COUNTER_XREF_RECORDS_READ = "xref_records_read";
    public static final String COUNTER_SYSOUT_LINES = "sysout_lines";

    private static final int APPL_AOK = 0;
    private static final int APPL_EOF = 16;

    private final Function<Optional<Path>, XrefFile> xrefFileFactory;

    public Cbact03cJob() {
        this(KsdsXrefFile::new);
    }

    /** Lets tests substitute the file (fault injection for READ/CLOSE errors that a KSDS cannot be made to return). */
    Cbact03cJob(Function<Optional<Path>, XrefFile> xrefFileFactory) {
        this.xrefFileFactory = xrefFileFactory;
    }

    @Override
    public String programId() {
        return PROGRAM_ID;
    }

    /** {@code xref_listing}: CARD-XREF-RECORD (CVACT03Y) columns, FILLER dropped. */
    public static DatasetSchema xrefListingSchema() {
        return DatasetSchema.fromLayout(XREF_LISTING,
                "CBACT03C: card cross-reference records (CVACT03Y) in CARDXREF KSDS key order, one row per record read",
                Copybooks.CARD_XREF_RECORD);
    }

    /** {@code sysout}: the DISPLAY lines, in order. */
    public static DatasetSchema sysoutSchema() {
        return new DatasetSchema(SYSOUT, "CBACT03C: SYSOUT DISPLAY lines in output order", List.of(
                new Column("line_no", ColumnType.INT, false, "1-based SYSOUT line number", Map.of()),
                new Column("line_text", ColumnType.STRING, false, "DISPLAY line text, right-trimmed", Map.of())));
    }

    @Override
    public JobResult run(JobContext ctx) {
        return new Execution(ctx, xrefFileFactory.apply(ctx.optionalInput(XREFFILE_DD))).procedureDivision();
    }

    /**
     * {@code 9910-DISPLAY-IO-STATUS} line for a two-character file status. A numeric status is shown as
     * {@code 00nn}; a non-numeric one or a {@code 9x} status is shown as status byte 1 followed by the
     * binary value of byte 2 (its code point; ASCII here) in three digits.
     */
    static String ioStatusLine(String ioStatus) {
        // 9910-DISPLAY-IO-STATUS
        char stat1 = ioStatus.charAt(0);
        char stat2 = ioStatus.charAt(1);
        String ioStatus04;
        if (!isNumeric(ioStatus) || stat1 == '9') {
            int twoBytesBinary = stat2 & 0xFF;
            ioStatus04 = stat1 + String.format("%03d", twoBytesBinary % 1000);
        } else {
            ioStatus04 = "00" + ioStatus;
        }
        return "FILE STATUS IS: NNNN" + ioStatus04;
    }

    private static boolean isNumeric(String s) {
        return s.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** One run of the program: its WORKING-STORAGE and paragraphs. */
    private static final class Execution {

        private final JobContext ctx;
        private final XrefFile xrefFile;
        private final DatasetWriter sysout;
        private final DatasetWriter xrefListing;

        // WORKING-STORAGE
        private String cardXrefRecord = " ".repeat(Copybooks.CARD_XREF_RECORD.length());
        private String xreffileStatus = "00";
        private String ioStatus = "00";
        private int applResult;
        private char endOfFile = 'N';

        private long xrefRecordsRead;
        private int sysoutLines;

        Execution(JobContext ctx, XrefFile xrefFile) {
            this.ctx = ctx;
            this.xrefFile = xrefFile;
            this.sysout = ctx.openDataset(SYSOUT, sysoutSchema(), OutputFormat.CSV);
            this.xrefListing = ctx.openDataset(XREF_LISTING, xrefListingSchema(), OutputFormat.PARQUET);
        }

        // PROCEDURE DIVISION
        JobResult procedureDivision() {
            display("START OF EXECUTION OF PROGRAM CBACT03C");
            xreffileOpen();

            while (endOfFile != 'Y') {
                if (endOfFile == 'N') {
                    xreffileGetNext();
                    if (endOfFile == 'N') {
                        display(cardXrefRecord);
                    }
                }
            }

            xreffileClose();

            display("END OF EXECUTION OF PROGRAM CBACT03C");

            Map<String, Long> counters = new LinkedHashMap<>();
            counters.put(COUNTER_XREF_RECORDS_READ, xrefRecordsRead);
            counters.put(COUNTER_SYSOUT_LINES, (long) sysoutLines);
            // GOBACK: RETURN-CODE is never set
            return JobResult.ok(counters);
        }

        // 1000-XREFFILE-GET-NEXT
        void xreffileGetNext() {
            xreffileStatus = xrefFile.read();
            if (xreffileStatus.equals("00")) {
                readInto(xrefFile.record());
                applResult = 0;
                display(cardXrefRecord);
            } else {
                if (xreffileStatus.equals("10")) {
                    applResult = 16;
                } else {
                    applResult = 12;
                }
            }
            if (applResult == APPL_AOK) {
                // CONTINUE
            } else {
                if (applResult == APPL_EOF) {
                    endOfFile = 'Y';
                } else {
                    display("ERROR READING XREFFILE");
                    ioStatus = xreffileStatus;
                    displayIoStatus();
                    abendProgram("READ XREFFILE status " + xreffileStatus);
                }
            }
        }

        // 0000-XREFFILE-OPEN
        void xreffileOpen() {
            applResult = 8;
            xreffileStatus = xrefFile.open();
            if (xreffileStatus.equals("00")) {
                applResult = 0;
            } else {
                applResult = 12;
            }
            if (applResult == APPL_AOK) {
                // CONTINUE
            } else {
                display("ERROR OPENING XREFFILE");
                ioStatus = xreffileStatus;
                displayIoStatus();
                abendProgram("OPEN XREFFILE status " + xreffileStatus);
            }
        }

        // 9000-XREFFILE-CLOSE
        void xreffileClose() {
            applResult = 8;
            xreffileStatus = xrefFile.close();
            if (xreffileStatus.equals("00")) {
                applResult = 0;
            } else {
                applResult = 12;
            }
            if (applResult == APPL_AOK) {
                // CONTINUE
            } else {
                display("ERROR CLOSING XREFFILE");
                ioStatus = xreffileStatus;
                displayIoStatus();
                abendProgram("CLOSE XREFFILE status " + xreffileStatus);
            }
        }

        // 9999-ABEND-PROGRAM
        void abendProgram(String reason) {
            display("ABENDING PROGRAM");
            // CALL 'CEE3ABD' USING ABCODE (999), TIMING (0)
            throw new AbendException(ABEND_CODE, PROGRAM_ID + ": " + reason);
        }

        // 9910-DISPLAY-IO-STATUS
        void displayIoStatus() {
            display(ioStatusLine(ioStatus));
        }

        /** {@code READ XREFFILE-FILE INTO CARD-XREF-RECORD}: group move of the 50-byte FD record. */
        private void readInto(String fdRecord) {
            cardXrefRecord = fdRecord;
            CobolRecord rec = Copybooks.CARD_XREF_RECORD.parse(fdRecord);
            xrefListing.write(rec.toColumns());
            xrefRecordsRead++;
        }

        /** DISPLAY: the full image to SYSOUT, and a right-trimmed row in the {@code sysout} dataset. */
        private void display(String text) {
            ctx.sysout().println(text);
            sysoutLines++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("line_no", sysoutLines);
            row.put("line_text", rtrim(text));
            sysout.write(row);
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
