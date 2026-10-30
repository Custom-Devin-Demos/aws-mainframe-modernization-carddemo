package com.carddemo.batch.common.record;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable record instance of a {@link RecordLayout}, the Java equivalent of a COBOL 01-level area.
 * Values are keyed by COBOL field name. Alphanumerics keep their space padding so comparisons match
 * COBOL semantics; writers right-trim when emitting Parquet/CSV.
 */
public final class CobolRecord {

    private final RecordLayout layout;
    private final Map<String, Object> values;

    CobolRecord(RecordLayout layout, Map<String, Object> values) {
        this.layout = layout;
        this.values = values;
    }

    public RecordLayout layout() {
        return layout;
    }

    public String getString(String field) {
        return (String) require(field);
    }

    /** Alphanumeric value with trailing spaces removed. */
    public String getTrimmed(String field) {
        return RecordLayout.rtrim(getString(field));
    }

    public BigDecimal getDecimal(String field) {
        return (BigDecimal) require(field);
    }

    public Object get(String field) {
        return require(field);
    }

    /** Sets a value with COBOL MOVE semantics (pad/truncate alphanumerics, rescale decimals). */
    public CobolRecord set(String field, Object value) {
        FieldSpec spec = layout.field(field);
        values.put(field, RecordLayout.coerce(spec, value));
        return this;
    }

    public CobolRecord copy() {
        return new CobolRecord(layout, new LinkedHashMap<>(values));
    }

    /** Values keyed by Parquet column name, alphanumerics right-trimmed. Fillers are omitted. */
    public Map<String, Object> toColumns() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldSpec f : layout.fields()) {
            if (f.isFiller()) {
                continue;
            }
            Object v = values.get(f.cobolName());
            out.put(f.columnName(), v instanceof String s && f.kind() == FieldSpec.Kind.ALPHANUMERIC ? RecordLayout.rtrim(s) : v);
        }
        return out;
    }

    /** Fixed-width image of the record, as a COBOL WRITE would produce it. */
    public String toFixedWidth() {
        return layout.format(this);
    }

    private Object require(String field) {
        layout.field(field);
        return values.get(field);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CobolRecord other && layout.name().equals(other.layout.name()) && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(layout.name(), values);
    }

    @Override
    public String toString() {
        return layout.name() + values;
    }
}
