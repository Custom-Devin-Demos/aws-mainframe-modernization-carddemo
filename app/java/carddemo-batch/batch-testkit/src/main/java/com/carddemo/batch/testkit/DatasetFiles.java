package com.carddemo.batch.testkit;

import com.carddemo.batch.common.output.ParquetDatasetWriter;
import com.carddemo.batch.common.output.Values;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.io.LocalInputFile;

/** Reads job outputs and expected fixtures into rows of canonical strings for comparison. */
public final class DatasetFiles {

    private DatasetFiles() {
    }

    /** Parquet rows in file order; values rendered with {@link Values#canonical}. */
    public static List<Map<String, String>> readParquet(Path file) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (ParquetReader<GenericRecord> r = AvroParquetReader.<GenericRecord>builder(new LocalInputFile(file))
                .withDataModel(ParquetDatasetWriter.dataModel()).build()) {
            GenericRecord rec;
            while ((rec = r.read()) != null) {
                Map<String, String> row = new LinkedHashMap<>();
                for (Schema.Field f : rec.getSchema().getFields()) {
                    Object v = rec.get(f.name());
                    row.put(f.name(), Values.canonical(v instanceof org.apache.avro.util.Utf8 u ? u.toString() : v));
                }
                rows.add(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    /** RFC 4180 CSV with header row (the format written by {@code CsvDatasetWriter} and used for fixtures). */
    public static List<Map<String, String>> readCsv(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<List<String>> records = parseCsv(text);
        if (records.isEmpty()) {
            throw new IllegalArgumentException(file + " has no header row");
        }
        List<String> header = records.get(0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> rec = records.get(i);
            if (rec.size() != header.size()) {
                throw new IllegalArgumentException(file + " row " + i + ": expected " + header.size() + " fields, got " + rec.size());
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                row.put(header.get(c), rec.get(c));
            }
            rows.add(row);
        }
        return rows;
    }

    static List<List<String>> parseCsv(String text) {
        List<List<String>> out = new ArrayList<>();
        List<String> rec = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
                any = true;
            } else if (c == ',') {
                rec.add(field.toString());
                field.setLength(0);
                any = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (any || field.length() > 0) {
                    rec.add(field.toString());
                    out.add(rec);
                }
                rec = new ArrayList<>();
                field.setLength(0);
                any = false;
            } else {
                field.append(c);
                any = true;
            }
        }
        if (any || field.length() > 0) {
            rec.add(field.toString());
            out.add(rec);
        }
        return out;
    }
}
