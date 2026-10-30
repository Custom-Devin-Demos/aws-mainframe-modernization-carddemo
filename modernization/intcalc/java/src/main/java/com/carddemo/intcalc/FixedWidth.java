package com.carddemo.intcalc;

/** Small helpers for fixed-width record layouts. */
final class FixedWidth {

    private FixedWidth() {
    }

    /** Left-justifies and space-pads (or truncates) like a COBOL alphanumeric MOVE. */
    static String alpha(String value, int length) {
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        return value + " ".repeat(length - value.length());
    }

    static String field(String record, int offset, int length) {
        return record.substring(offset, offset + length);
    }

    /** Normalises a line-sequential record: drops a trailing CR and pads to the record length. */
    static String normalise(String line, int length) {
        String s = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
        if (s.length() > length) {
            throw new IllegalArgumentException(
                    "record longer than " + length + " bytes: '" + s + "'");
        }
        return alpha(s, length);
    }
}
