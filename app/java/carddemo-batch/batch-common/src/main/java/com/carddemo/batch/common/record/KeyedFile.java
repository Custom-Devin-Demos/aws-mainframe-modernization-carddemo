package com.carddemo.batch.common.record;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * In-memory replacement for a VSAM KSDS: random READ/WRITE/REWRITE/DELETE by primary key and
 * sequential browse in ascending key order. Keys are the concatenated raw images of the key
 * fields, so ordering matches the KSDS collating sequence for these all-display keys.
 * Return values mirror the VSAM file-status outcomes ('00', '22' duplicate, '23' not found).
 */
public final class KeyedFile {

    public enum Status { OK, DUPLICATE_KEY, NOT_FOUND }

    private final RecordLayout layout;
    private final List<String> keyFields;
    private final TreeMap<String, CobolRecord> records = new TreeMap<>();

    public KeyedFile(RecordLayout layout, String... keyFields) {
        this.layout = layout;
        this.keyFields = List.of(keyFields);
        this.keyFields.forEach(layout::field);
    }

    /** Loads an ASCII unload (e.g. {@code app/data/ASCII/acctdata.txt}); duplicate keys are rejected. */
    public static KeyedFile load(Path file, RecordLayout layout, String... keyFields) {
        KeyedFile kf = new KeyedFile(layout, keyFields);
        FixedWidthFile.forEach(file, layout, rec -> {
            if (kf.write(rec) != Status.OK) {
                throw new IllegalArgumentException(file.getFileName() + ": duplicate key " + kf.keyOf(rec));
            }
        });
        return kf;
    }

    public RecordLayout layout() {
        return layout;
    }

    public String keyOf(CobolRecord rec) {
        StringBuilder sb = new StringBuilder();
        for (String f : keyFields) {
            sb.append(layout.format(rec), layout.offset(f), layout.offset(f) + layout.field(f).length());
        }
        return sb.toString();
    }

    /** Builds a key from raw field images in key-field order, padding each to its picture width. */
    public String key(String... parts) {
        if (parts.length != keyFields.size()) {
            throw new IllegalArgumentException("expected " + keyFields.size() + " key parts");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            FieldSpec f = layout.field(keyFields.get(i));
            sb.append((String) RecordLayout.coerce(f, parts[i]));
        }
        return sb.toString();
    }

    /** READ ... INVALID KEY: returns a copy so callers must REWRITE to persist changes. */
    public Optional<CobolRecord> read(String key) {
        CobolRecord rec = records.get(key);
        return rec == null ? Optional.empty() : Optional.of(rec.copy());
    }

    public Status write(CobolRecord rec) {
        return records.putIfAbsent(keyOf(rec), rec.copy()) == null ? Status.OK : Status.DUPLICATE_KEY;
    }

    public Status rewrite(CobolRecord rec) {
        String key = keyOf(rec);
        if (!records.containsKey(key)) {
            return Status.NOT_FOUND;
        }
        records.put(key, rec.copy());
        return Status.OK;
    }

    public Status delete(String key) {
        return records.remove(key) == null ? Status.NOT_FOUND : Status.OK;
    }

    /** Records in ascending key order (sequential browse). */
    public Collection<CobolRecord> inKeyOrder() {
        return records.values().stream().map(CobolRecord::copy).toList();
    }

    public int size() {
        return records.size();
    }
}
