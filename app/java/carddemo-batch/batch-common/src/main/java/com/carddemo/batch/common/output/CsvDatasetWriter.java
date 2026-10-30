package com.carddemo.batch.common.output;

import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** RFC 4180 CSV: UTF-8, header row, comma separator, LF line endings, quotes only when needed. */
public final class CsvDatasetWriter implements DatasetWriter {

    private final DatasetSchema schema;
    private final BufferedWriter out;
    private long rows;

    public CsvDatasetWriter(Path file, DatasetSchema schema) {
        this.schema = schema;
        try {
            this.out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            StringBuilder header = new StringBuilder();
            for (Column c : schema.columns()) {
                if (header.length() > 0) {
                    header.append(',');
                }
                header.append(escape(c.name()));
            }
            out.write(header.append('\n').toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(Map<String, Object> row) {
        StringBuilder line = new StringBuilder();
        boolean first = true;
        for (Column c : schema.columns()) {
            if (!row.containsKey(c.name())) {
                throw new IllegalArgumentException(schema.name() + ": missing column " + c.name());
            }
            if (!first) {
                line.append(',');
            }
            first = false;
            line.append(escape(Values.canonical(row.get(c.name()))));
        }
        try {
            out.write(line.append('\n').toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        rows++;
    }

    public static String escape(String s) {
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0
                || (!s.isEmpty() && (s.charAt(0) == ' ' || s.charAt(s.length() - 1) == ' '))) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    @Override
    public long rowCount() {
        return rows;
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
