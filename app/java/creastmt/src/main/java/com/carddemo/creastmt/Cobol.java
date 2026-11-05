package com.carddemo.creastmt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** COBOL data-movement semantics needed to reproduce CBSTM03A output byte for byte. */
final class Cobol {

    private Cobol() {
    }

    /** MOVE alphanumeric to PIC X(n): left-justify, pad with spaces or truncate on the right. */
    static String pic(String value, int length) {
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        return value + " ".repeat(length - value.length());
    }

    /** Fixed-position field of a record (offset is 0-based). */
    static String field(String record, int offset, int length) {
        return record.substring(offset, offset + length);
    }

    /** Unsigned zoned PIC 9(n) field. */
    static long unsigned(String digits) {
        return Long.parseLong(digits);
    }

    /** PIC 9(n) rendered as stored: zero-padded to n digits. */
    static String digits(long value, int length) {
        return String.format("%0" + length + "d", value);
    }

    /**
     * Signed zoned decimal PIC S9(i)V9(scale) with a trailing overpunched sign, as found in the
     * sample data ({, A-I positive; }, J-R negative). Plain digits are treated as positive.
     */
    static BigDecimal zoned(String text, int scale) {
        StringBuilder digits = new StringBuilder(text.length());
        boolean negative = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (i < text.length() - 1) {
                if (c < '0' || c > '9') {
                    throw new IllegalArgumentException("Invalid zoned decimal: '" + text + "'");
                }
                digits.append(c);
                continue;
            }
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (c == '{') {
                digits.append('0');
            } else if (c >= 'A' && c <= 'I') {
                digits.append((char) ('1' + (c - 'A')));
            } else if (c == '}') {
                digits.append('0');
                negative = true;
            } else if (c >= 'J' && c <= 'R') {
                digits.append((char) ('1' + (c - 'J')));
                negative = true;
            } else {
                throw new IllegalArgumentException("Invalid zoned decimal sign: '" + text + "'");
            }
        }
        BigDecimal value = new BigDecimal(new BigInteger(digits.toString()), scale);
        return negative ? value.negate() : value;
    }

    /** Store into a numeric item with {@code integerDigits} integer positions (high-order truncation, no ON SIZE ERROR). */
    static BigDecimal truncate(BigDecimal value, int integerDigits, int scale) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.DOWN);
        return scaled.remainder(BigDecimal.TEN.pow(integerDigits));
    }

    /** PIC 9(n).99- */
    static String editZeroFilled(BigDecimal value, int integerDigits) {
        return edit(value, integerDigits, false);
    }

    /** PIC Z(n).99- */
    static String editZeroSuppressed(BigDecimal value, int integerDigits) {
        return edit(value, integerDigits, true);
    }

    private static String edit(BigDecimal value, int integerDigits, boolean suppress) {
        BigDecimal v = truncate(value, integerDigits, 2);
        String unscaled = digits(v.abs().unscaledValue().longValueExact(), integerDigits + 2);
        String intPart = unscaled.substring(0, integerDigits);
        if (suppress) {
            int firstNonZero = 0;
            while (firstNonZero < intPart.length() && intPart.charAt(firstNonZero) == '0') {
                firstNonZero++;
            }
            intPart = " ".repeat(firstNonZero) + intPart.substring(firstNonZero);
        }
        return intPart + "." + unscaled.substring(integerDigits) + (v.signum() < 0 ? "-" : " ");
    }

    /** The portion of {@code source} before the first occurrence of {@code delimiter} (STRING ... DELIMITED BY). */
    static String delimitedBy(String source, String delimiter) {
        int at = source.indexOf(delimiter);
        return at < 0 ? source : source.substring(0, at);
    }

    /**
     * STRING ... INTO target, where target was first set to SPACES: pieces are concatenated until
     * the receiving item is full (no ON OVERFLOW), the remainder keeps its spaces.
     */
    static String stringInto(int targetLength, String... pieces) {
        StringBuilder out = new StringBuilder(targetLength);
        for (String piece : pieces) {
            out.append(piece);
        }
        return pic(out.toString(), targetLength);
    }
}
