package com.carddemo.batch.cbact02c;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * CBACT02C (JCL {@code READCARD} STEP05): reads the card master KSDS (DD {@code CARDFILE}, copybook
 * CVACT02Y) sequentially in key order and DISPLAYs every {@code CARD-RECORD}.
 *
 * <p>Outputs, published only when the program ends normally (an abend publishes nothing):
 * {@code card_listing} (Parquet, one row per card in key order) and {@code sysout} (CSV, every DISPLAY
 * line). Rules: {@code docs/modernization/business-rules/CBACT02C.md}.
 */
public final class Cbact02cJob implements BatchJob {

    public static final String PROGRAM_ID = "CBACT02C";
    public static final String CARDFILE_DD = "CARDFILE";
    public static final String CARD_LISTING = "card_listing";
    public static final String SYSOUT = "sysout";
    public static final int ABEND_CODE = 999;
    public static final String CARD_RECORDS_READ = "card_records_read";
    public static final String SYSOUT_LINES = "sysout_lines";

    public static final DatasetSchema CARD_LISTING_SCHEMA = DatasetSchema.fromLayout(CARD_LISTING,
            "CBACT02C: card master records (CVACT02Y CARD-RECORD) in CARDFILE KSDS key order, as DISPLAYed",
            Copybooks.CARD_RECORD);

    public static final DatasetSchema SYSOUT_SCHEMA = new DatasetSchema(SYSOUT,
            "CBACT02C: SYSOUT DISPLAY lines in order",
            List.of(Column.of("line_no", ColumnType.INT, "1-based DISPLAY line number"),
                    Column.of("line_text", ColumnType.STRING, "DISPLAY line image, right-trimmed")));

    static final String START_MESSAGE = "START OF EXECUTION OF PROGRAM CBACT02C";
    static final String END_MESSAGE = "END OF EXECUTION OF PROGRAM CBACT02C";

    private final Function<JobContext, CardFile> cardFiles;

    public Cbact02cJob() {
        this(ctx -> new KsdsCardFile(ctx.optionalInput(CARDFILE_DD)));
    }

    /** Test seam: substitutes the CARDFILE implementation (e.g. to inject file statuses). */
    Cbact02cJob(Function<JobContext, CardFile> cardFiles) {
        this.cardFiles = cardFiles;
    }

    @Override
    public String programId() {
        return PROGRAM_ID;
    }

    @Override
    public JobResult run(JobContext ctx) {
        return new Execution(ctx, cardFiles.apply(ctx)).procedureDivision();
    }

    /** One run of the program; fields are its WORKING-STORAGE. */
    private static final class Execution {

        private static final int APPL_AOK = 0;
        private static final int APPL_EOF = 16;

        private final JobContext ctx;
        private final CardFile cardfileFile;
        private final DisplayLog sysout;
        private final List<CobolRecord> cardListing = new ArrayList<>();

        private String cardRecord;
        private String cardfileStatus = "  ";
        private String ioStatus = "  ";
        private int applResult;
        private boolean endOfFile;

        Execution(JobContext ctx, CardFile cardfileFile) {
            this.ctx = ctx;
            this.cardfileFile = cardfileFile;
            this.sysout = new DisplayLog(ctx.sysout());
        }

        // PROCEDURE DIVISION (main line)
        JobResult procedureDivision() {
            sysout.display(START_MESSAGE);
            cardfileOpen();
            while (!endOfFile) {
                cardfileGetNext();
                if (!endOfFile) {
                    displayCardRecord();
                }
            }
            cardfileClose();
            sysout.display(END_MESSAGE);
            publishOutputs();
            Map<String, Long> counters = new LinkedHashMap<>();
            counters.put(CARD_RECORDS_READ, (long) cardListing.size());
            counters.put(SYSOUT_LINES, (long) sysout.lines().size());
            return JobResult.ok(counters);
        }

        // DISPLAY CARD-RECORD
        private void displayCardRecord() {
            sysout.display(cardRecord);
            cardListing.add(Copybooks.CARD_RECORD.parse(cardRecord));
        }

        // 1000-CARDFILE-GET-NEXT
        private void cardfileGetNext() {
            CardFile.ReadResult read = cardfileFile.readNext();
            cardfileStatus = read.status();
            if (CardFile.OK.equals(cardfileStatus)) {
                cardRecord = read.recordImage();
                applResult = APPL_AOK;
            } else if (CardFile.END_OF_FILE.equals(cardfileStatus)) {
                applResult = APPL_EOF;
            } else {
                applResult = 12;
            }
            if (applResult == APPL_AOK) {
                return;
            }
            if (applResult == APPL_EOF) {
                endOfFile = true;
            } else {
                sysout.display("ERROR READING CARDFILE");
                ioStatus = cardfileStatus;
                displayIoStatus();
                abendProgram("READ");
            }
        }

        // 0000-CARDFILE-OPEN
        private void cardfileOpen() {
            applResult = 8;
            cardfileStatus = cardfileFile.open();
            applResult = CardFile.OK.equals(cardfileStatus) ? APPL_AOK : 12;
            if (applResult != APPL_AOK) {
                sysout.display("ERROR OPENING CARDFILE");
                ioStatus = cardfileStatus;
                displayIoStatus();
                abendProgram("OPEN");
            }
        }

        // 9000-CARDFILE-CLOSE
        private void cardfileClose() {
            applResult = 8;
            cardfileStatus = cardfileFile.close();
            applResult = CardFile.OK.equals(cardfileStatus) ? APPL_AOK : 12;
            if (applResult != APPL_AOK) {
                sysout.display("ERROR CLOSING CARDFILE");
                ioStatus = cardfileStatus;
                displayIoStatus();
                abendProgram("CLOSE");
            }
        }

        // 9999-ABEND-PROGRAM
        private void abendProgram(String operation) {
            sysout.display("ABENDING PROGRAM");
            String detail = cardfileFile.lastError();
            throw new AbendException(ABEND_CODE, PROGRAM_ID + ": " + operation + " CARDFILE failed with file status "
                    + cardfileStatus + (detail == null ? "" : " (" + detail + ")"));
        }

        // 9910-DISPLAY-IO-STATUS
        private void displayIoStatus() {
            sysout.display(IoStatus.displayLine(ioStatus));
        }

        /** Datasets are written after the last DISPLAY, so an abend leaves no partial output. */
        private void publishOutputs() {
            try (DatasetWriter listing = ctx.openDataset(CARD_LISTING, CARD_LISTING_SCHEMA, OutputFormat.PARQUET)) {
                cardListing.forEach(listing::write);
            }
            try (DatasetWriter lines = ctx.openDataset(SYSOUT, SYSOUT_SCHEMA, OutputFormat.CSV)) {
                List<String> all = sysout.lines();
                for (int i = 0; i < all.size(); i++) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("line_no", i + 1);
                    row.put("line_text", DisplayLog.rtrim(all.get(i)));
                    lines.write(row);
                }
            }
        }
    }
}
