package com.carddemo.interest.domain;

import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Trace(copybook = "CVTRA01Y", note = "zoned-decimal / PIC X / PIC 9 field codec shared by all copybook records")
final class CobolFields {

    private CobolFields() {
    }

    static String picX(String record, int offset, int length) {
        int end = offset + length;
        return record.substring(offset, end).replaceFirst(" +$", "");
    }

    static String pic9(String record, int offset, int length) {
        String value = record.substring(offset, offset + length);
        if (!value.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new IllegalArgumentException("PIC 9 field contains non-digit characters");
        }
        return value;
    }

    static BigDecimal picS9V99(String record, int offset, int length) {
        String value = record.substring(offset, offset + length);
        String digits = value.substring(0, length - 1);
        if (!digits.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new IllegalArgumentException("zoned decimal field contains non-digit characters");
        }

        char overpunch = value.charAt(length - 1);
        boolean negative;
        char finalDigit;
        if (overpunch >= '0' && overpunch <= '9') {
            negative = false;
            finalDigit = overpunch;
        } else if (overpunch == '{' || overpunch == '}') {
            negative = overpunch == '}';
            finalDigit = '0';
        } else if (overpunch >= 'A' && overpunch <= 'I') {
            negative = false;
            finalDigit = (char) ('0' + overpunch - 'A' + 1);
        } else if (overpunch >= 'J' && overpunch <= 'R') {
            negative = true;
            finalDigit = (char) ('0' + overpunch - 'J' + 1);
        } else {
            throw new IllegalArgumentException("invalid zoned decimal overpunch: " + overpunch);
        }

        BigDecimal result = new BigDecimal(digits + finalDigit).movePointLeft(2);
        return negative ? result.negate() : result;
    }

    static String formatX(String value, int length) {
        String actual = value == null ? "" : value;
        if (actual.length() > length) {
            throw new IllegalArgumentException("PIC X value exceeds field length " + length);
        }
        return actual + " ".repeat(length - actual.length());
    }

    static String format9(String value, int length) {
        if (value == null) {
            throw new IllegalArgumentException("PIC 9 value must not be null");
        }
        String actual = value;
        if (!actual.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new IllegalArgumentException("PIC 9 value contains non-digit characters");
        }
        if (actual.length() > length) {
            throw new IllegalArgumentException("PIC 9 value exceeds field length " + length);
        }
        return "0".repeat(length - actual.length()) + actual;
    }

    static String formatS9V99(BigDecimal value, int length) {
        BigDecimal scaled = value.setScale(2, RoundingMode.UNNECESSARY);
        String digits = scaled.abs().unscaledValue().toString();
        if (digits.length() > length) {
            throw new IllegalArgumentException("zoned decimal value exceeds field length " + length);
        }
        digits = "0".repeat(length - digits.length()) + digits;
        int lastDigit = digits.charAt(length - 1) - '0';
        char overpunch;
        if (scaled.signum() < 0) {
            overpunch = lastDigit == 0 ? '}' : (char) ('J' + lastDigit - 1);
        } else {
            overpunch = lastDigit == 0 ? '{' : (char) ('A' + lastDigit - 1);
        }
        return digits.substring(0, length - 1) + overpunch;
    }

    static String checkLength(String record, int expected) {
        String stripped = record;
        if (stripped.endsWith("\r\n")) {
            stripped = stripped.substring(0, stripped.length() - 2);
        } else if (stripped.endsWith("\n") || stripped.endsWith("\r")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        if (stripped.length() != expected) {
            throw new IllegalArgumentException("record length " + stripped.length() + ", expected " + expected);
        }
        return stripped;
    }

    static BigDecimal scale2(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " must have scale 2", exception);
        }
    }
}
