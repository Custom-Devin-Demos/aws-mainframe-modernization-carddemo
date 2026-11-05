package com.carddemo.online.common;

/** Helpers for COBOL alphanumeric semantics. */
public final class CobolText {

    private CobolText() {
    }

    /** True for the COBOL tests {@code = SPACES OR LOW-VALUES} (null, empty, blanks or NULs). */
    public static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ' ' && c != '\u0000') {
                return false;
            }
        }
        return true;
    }

    /** True if the trimmed value is non-empty and all digits ({@code IS NUMERIC} on a filled field). */
    public static boolean isNumeric(String value) {
        if (isBlank(value)) {
            return false;
        }
        String v = value.trim();
        for (int i = 0; i < v.length(); i++) {
            if (!Character.isDigit(v.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Right-trims trailing spaces/NULs, like moving a PIC X field into a varchar. */
    public static String rtrim(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\u0000')) {
            end--;
        }
        return value.substring(0, end);
    }
}
