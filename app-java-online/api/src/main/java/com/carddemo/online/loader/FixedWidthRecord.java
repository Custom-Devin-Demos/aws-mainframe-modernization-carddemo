package com.carddemo.online.loader;

import com.carddemo.online.common.CobolText;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** A single copybook record; offsets are 1-based like COBOL reference modification. */
public final class FixedWidthRecord {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS]");

    private final String data;

    public FixedWidthRecord(String data) {
        this.data = data;
    }

    public String raw(int start, int length) {
        return data.substring(start - 1, start - 1 + length);
    }

    /** PIC X field, right-trimmed. */
    public String text(int start, int length) {
        return CobolText.rtrim(raw(start, length));
    }

    /** Unsigned PIC 9 field. */
    public long number(int start, int length) {
        String v = raw(start, length).trim();
        return v.isEmpty() ? 0 : Long.parseLong(v);
    }

    /** Signed zoned PIC S9(n)V9(scale). */
    public BigDecimal decimal(int start, int length, int scale) {
        return ZonedDecimal.parse(raw(start, length), scale);
    }

    /** X(10) 'YYYY-MM-DD'; blank or invalid dates become null. */
    public LocalDate date(int start) {
        String v = text(start, 10).trim();
        if (v.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** X(26) 'YYYY-MM-DD HH:MM:SS.ffffff' (DB2 style, '-' or ' ' separator); blank becomes null. */
    public LocalDateTime timestamp(int start) {
        String v = text(start, 26).trim();
        if (v.isEmpty()) {
            return null;
        }
        if (v.length() > 10 && v.charAt(10) == '-') {
            v = v.substring(0, 10) + ' ' + v.substring(11).replaceFirst("^(\\d\\d)\\.(\\d\\d)\\.(\\d\\d)", "$1:$2:$3");
        }
        try {
            return LocalDateTime.parse(v, TS);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
