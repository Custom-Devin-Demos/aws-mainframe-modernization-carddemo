package com.carddemo.online.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads the records of a {@link Dataset} from app/data in either encoding. */
public final class DatasetReader {

    static final Charset EBCDIC = Charset.forName("IBM037");

    private final Path dataDir;
    private final DataEncoding preferred;

    public DatasetReader(Path dataDir, DataEncoding preferred) {
        this.dataDir = dataDir;
        this.preferred = preferred;
    }

    /** Reads with the preferred encoding, falling back to the other one when that file does not exist. */
    public List<FixedWidthRecord> read(Dataset dataset) {
        DataEncoding encoding = preferred;
        if (resolve(dataset, encoding) == null) {
            encoding = encoding == DataEncoding.ASCII ? DataEncoding.EBCDIC : DataEncoding.ASCII;
        }
        Path file = resolve(dataset, encoding);
        if (file == null) {
            throw new IllegalStateException("No sample data file for " + dataset + " under " + dataDir);
        }
        return read(dataset, encoding, file);
    }

    public List<FixedWidthRecord> read(Dataset dataset, DataEncoding encoding) {
        Path file = resolve(dataset, encoding);
        if (file == null) {
            throw new IllegalStateException("No " + encoding + " file for " + dataset + " under " + dataDir);
        }
        return read(dataset, encoding, file);
    }

    private Path resolve(Dataset dataset, DataEncoding encoding) {
        String name = encoding == DataEncoding.ASCII ? dataset.asciiFile() : dataset.ebcdicFile();
        if (name == null) {
            return null;
        }
        Path p = dataDir.resolve(encoding.name()).resolve(name);
        return Files.isRegularFile(p) ? p : null;
    }

    private static List<FixedWidthRecord> read(Dataset dataset, DataEncoding encoding, Path file) {
        int len = dataset.recordLength();
        List<FixedWidthRecord> out = new ArrayList<>();
        try {
            if (encoding == DataEncoding.EBCDIC) {
                byte[] bytes = Files.readAllBytes(file);
                for (int off = 0; off + len <= bytes.length; off += len) {
                    out.add(new FixedWidthRecord(new String(bytes, off, len, EBCDIC)));
                }
            } else {
                for (String line : Files.readAllLines(file, StandardCharsets.US_ASCII)) {
                    String l = line.replace("\r", "");
                    if (l.isBlank()) {
                        continue;
                    }
                    out.add(new FixedWidthRecord(fit(l, len)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static String fit(String line, int len) {
        if (line.length() >= len) {
            return line.substring(0, len);
        }
        return line + " ".repeat(len - line.length());
    }
}
