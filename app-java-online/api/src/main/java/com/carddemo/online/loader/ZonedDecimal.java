package com.carddemo.online.loader;

import java.math.BigDecimal;

/**
 * Decodes COBOL DISPLAY (zoned) numerics such as {@code PIC S9(10)V99}. The sign is overpunched in
 * the last character; after EBCDIC-to-text decoding both encodings use the same characters:
 * {@code '{','A'-'I'} = +0..+9 and {@code '}','J'-'R'} = -0..-9. Plain digits are unsigned/positive.
 */
public final class ZonedDecimal {

    private ZonedDecimal() {
    }

    public static BigDecimal parse(String field, int scale) {
        String raw = field.replace('\u0000', ' ').trim();
        if (raw.isEmpty()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        char last = raw.charAt(raw.length() - 1);
        boolean negative = false;
        char digit;
        if (last >= '0' && last <= '9') {
            digit = last;
        } else if (last == '{') {
            digit = '0';
        } else if (last >= 'A' && last <= 'I') {
            digit = (char) ('1' + (last - 'A'));
        } else if (last == '}') {
            digit = '0';
            negative = true;
        } else if (last >= 'J' && last <= 'R') {
            digit = (char) ('1' + (last - 'J'));
            negative = true;
        } else {
            throw new IllegalArgumentException("Invalid zoned decimal: '" + field + "'");
        }
        String digits = raw.substring(0, raw.length() - 1) + digit;
        if (digits.startsWith("-") || digits.startsWith("+")) {
            negative = negative || digits.charAt(0) == '-';
            digits = digits.substring(1);
        }
        BigDecimal value = new BigDecimal(new java.math.BigInteger(digits), scale);
        return negative ? value.negate() : value;
    }
}
