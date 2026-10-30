package com.carddemo.batch.common.record;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A fixed-width record layout derived from a copybook (see {@link Copybooks}). */
public final class RecordLayout {

    private final String name;
    private final String copybook;
    private final List<FieldSpec> fields;
    private final Map<String, FieldSpec> byName = new LinkedHashMap<>();
    private final Map<String, Integer> offsets = new LinkedHashMap<>();
    private final int length;

    public RecordLayout(String name, String copybook, List<FieldSpec> fields) {
        this.name = name;
        this.copybook = copybook;
        this.fields = List.copyOf(fields);
        int pos = 0;
        for (FieldSpec f : this.fields) {
            if (!f.isFiller()) {
                if (byName.put(f.cobolName(), f) != null) {
                    throw new IllegalArgumentException("duplicate field " + f.cobolName() + " in " + name);
                }
                offsets.put(f.cobolName(), pos);
            }
            pos += f.length();
        }
        this.length = pos;
    }

    public String name() {
        return name;
    }

    public String copybook() {
        return copybook;
    }

    public List<FieldSpec> fields() {
        return fields;
    }

    public int length() {
        return length;
    }

    public FieldSpec field(String cobolName) {
        FieldSpec f = byName.get(cobolName);
        if (f == null) {
            throw new IllegalArgumentException("no field " + cobolName + " in " + name);
        }
        return f;
    }

    /** Zero-based byte offset of a field within the record. */
    public int offset(String cobolName) {
        field(cobolName);
        return offsets.get(cobolName);
    }

    /** A record with every field INITIALIZEd (spaces / zeros). */
    public CobolRecord newRecord() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldSpec f : fields) {
            if (!f.isFiller()) {
                values.put(f.cobolName(), initialValue(f));
            }
        }
        return new CobolRecord(this, values);
    }

    /** Parses one record image. Shorter lines are space-padded (trailing blanks are often stripped). */
    public CobolRecord parse(String line) {
        if (line.length() > length) {
            if (!line.substring(length).isBlank()) {
                throw new IllegalArgumentException(name + ": record longer than " + length + " bytes");
            }
            line = line.substring(0, length);
        }
        String image = pad(line, length);
        Map<String, Object> values = new LinkedHashMap<>();
        int pos = 0;
        for (FieldSpec f : fields) {
            String raw = image.substring(pos, pos + f.length());
            pos += f.length();
            if (f.isFiller()) {
                continue;
            }
            try {
                values.put(f.cobolName(), switch (f.kind()) {
                    case ALPHANUMERIC, NUMERIC_CODE -> raw;
                    case DECIMAL -> ZonedDecimal.decode(raw, f.scale(), f.signed());
                    case FILLER -> throw new IllegalStateException();
                });
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(name + "." + f.cobolName() + ": " + e.getMessage(), e);
            }
        }
        return new CobolRecord(this, values);
    }

    public String format(CobolRecord record) {
        StringBuilder sb = new StringBuilder(length);
        for (FieldSpec f : fields) {
            if (f.isFiller()) {
                sb.append(" ".repeat(f.length()));
                continue;
            }
            Object v = record.get(f.cobolName());
            sb.append(switch (f.kind()) {
                case ALPHANUMERIC, NUMERIC_CODE -> (String) v;
                case DECIMAL -> ZonedDecimal.encode((BigDecimal) v, f.integerDigits(), f.scale(), f.signed());
                case FILLER -> throw new IllegalStateException();
            });
        }
        return sb.toString();
    }

    /** Fields excluding FILLER, in layout order. */
    public List<FieldSpec> dataFields() {
        List<FieldSpec> out = new ArrayList<>();
        for (FieldSpec f : fields) {
            if (!f.isFiller()) {
                out.add(f);
            }
        }
        return Collections.unmodifiableList(out);
    }

    static Object initialValue(FieldSpec f) {
        return switch (f.kind()) {
            case ALPHANUMERIC, FILLER -> " ".repeat(f.length());
            case NUMERIC_CODE -> "0".repeat(f.length());
            case DECIMAL -> BigDecimal.ZERO.setScale(f.scale());
        };
    }

    static Object coerce(FieldSpec f, Object value) {
        return switch (f.kind()) {
            case ALPHANUMERIC -> {
                String s = String.valueOf(value);
                yield s.length() >= f.length() ? s.substring(0, f.length()) : pad(s, f.length());
            }
            case NUMERIC_CODE -> {
                String digits = value instanceof Number n ? new BigDecimal(n.toString()).toBigInteger().abs().toString() : String.valueOf(value).trim();
                if (!digits.chars().allMatch(Character::isDigit)) {
                    throw new IllegalArgumentException(f.cobolName() + ": not numeric '" + value + "'");
                }
                if (digits.length() > f.length()) {
                    digits = digits.substring(digits.length() - f.length());
                }
                yield "0".repeat(f.length() - digits.length()) + digits;
            }
            case DECIMAL -> {
                BigDecimal d = value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value).trim());
                // Round-trip through the picture so truncation matches a COBOL MOVE.
                yield ZonedDecimal.decode(ZonedDecimal.encode(d, f.integerDigits(), f.scale(), f.signed()), f.scale(), f.signed())
                        .setScale(f.scale(), RoundingMode.UNNECESSARY);
            }
            case FILLER -> throw new IllegalArgumentException("cannot set FILLER");
        };
    }

    static String pad(String s, int len) {
        return s.length() >= len ? s : s + " ".repeat(len - s.length());
    }

    static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}
