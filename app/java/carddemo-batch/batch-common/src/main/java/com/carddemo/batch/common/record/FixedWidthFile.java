package com.carddemo.batch.common.record;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Line-sequential replacement for QSAM/ESDS files and for the ASCII unload of a KSDS.
 * Records are newline-terminated; CR (from CRLF sample files) is stripped; blank lines are skipped.
 */
public final class FixedWidthFile {

    private FixedWidthFile() {
    }

    public static List<CobolRecord> read(Path file, RecordLayout layout) {
        List<CobolRecord> out = new ArrayList<>();
        forEach(file, layout, out::add);
        return out;
    }

    public static void forEach(Path file, RecordLayout layout, Consumer<CobolRecord> consumer) {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.US_ASCII)) {
            String line;
            int lineNo = 0;
            while ((line = r.readLine()) != null) {
                lineNo++;
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                if (line.isBlank()) {
                    continue;
                }
                try {
                    consumer.accept(layout.parse(line));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(file.getFileName() + ":" + lineNo + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void write(Path file, Iterable<CobolRecord> records) {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.US_ASCII)) {
            for (CobolRecord rec : records) {
                w.write(rec.toFixedWidth());
                w.write('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
