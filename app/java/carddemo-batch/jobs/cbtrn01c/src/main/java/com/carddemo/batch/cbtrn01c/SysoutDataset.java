package com.carddemo.batch.cbtrn01c;

import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.output.DatasetWriter;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.ColumnType;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DISPLAY: writes each line to the job's SYSOUT stream exactly as COBOL would, and to the {@code sysout}
 * CSV dataset ({@code line_no}, {@code line_text} right-trimmed). Candidate for batch-common.
 */
final class SysoutDataset implements AutoCloseable {

    static final String DATASET = "sysout";

    static final DatasetSchema SCHEMA = new DatasetSchema(DATASET, "CBTRN01C DISPLAY output (SYSOUT), one row per line", List.of(
            Column.of("line_no", ColumnType.INT, "1-based SYSOUT line number"),
            Column.of("line_text", ColumnType.STRING, "DISPLAY line, right-trimmed")));

    private final JobContext ctx;
    private final DatasetWriter writer;
    private int lineNo;

    SysoutDataset(JobContext ctx) {
        this.ctx = ctx;
        this.writer = ctx.openDataset(DATASET, SCHEMA, OutputFormat.CSV);
    }

    void display(String line) {
        ctx.sysout().print(line + "\n");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("line_no", ++lineNo);
        row.put("line_text", rtrim(line));
        writer.write(row);
    }

    static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }

    @Override
    public void close() {
        writer.close();
    }
}
