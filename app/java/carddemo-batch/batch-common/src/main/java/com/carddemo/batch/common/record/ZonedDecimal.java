package com.carddemo.batch.common.record;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Codec for COBOL zoned-decimal (USAGE DISPLAY) numerics as they appear in the ASCII sample data.
 *
 * <p>Signed fields carry the sign as an overpunch on the last byte. The sample data uses the
 * EBCDIC-derived convention ({@code '{'}, {@code 'A'-'I'} positive; {@code '}'}, {@code 'J'-'R'}
 * negative). GnuCOBOL's native ASCII convention ({@code 'p'-'y'} negative) is also accepted on
 * decode. Encoding always emits the EBCDIC-derived convention so files round-trip with the samples.
 */
public final class ZonedDecimal {

    private static final String POSITIVE = "{ABCDEFGHI";
    private static final String NEGATIVE = "}JKLMNOPQR";
    private static final String NEGATIVE_ASCII = "pqrstuvwxy";

    private ZonedDecimal() {
    }

    public static BigDecimal decode(String raw, int scale, boolean signed) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("empty zoned decimal");
        }
        StringBuilder digits = new StringBuilder(raw.length());
        boolean negative = false;
        int last = raw.length() - 1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (i == last && signed && POSITIVE.indexOf(c) >= 0) {
                digits.append((char) ('0' + POSITIVE.indexOf(c)));
            } else if (i == last && signed && NEGATIVE.indexOf(c) >= 0) {
                digits.append((char) ('0' + NEGATIVE.indexOf(c)));
                negative = true;
            } else if (i == last && signed && NEGATIVE_ASCII.indexOf(c) >= 0) {
                digits.append((char) ('0' + NEGATIVE_ASCII.indexOf(c)));
                negative = true;
            } else {
                throw new IllegalArgumentException("invalid zoned decimal '" + raw + "'");
            }
        }
        BigInteger unscaled = new BigInteger(digits.toString());
        return new BigDecimal(negative ? unscaled.negate() : unscaled, scale);
    }

    /**
     * Encodes like a COBOL MOVE into {@code PIC [S]9(intDigits)V9(scale)}: extra fractional digits
     * are truncated, high-order integer digits beyond the picture are dropped, and an unsigned
     * picture stores the absolute value.
     */
    public static String encode(BigDecimal value, int intDigits, int scale, boolean signed) {
        BigInteger unscaled = value.setScale(scale, RoundingMode.DOWN).unscaledValue();
        boolean negative = unscaled.signum() < 0;
        String digits = unscaled.abs().toString();
        int width = intDigits + scale;
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        }
        StringBuilder out = new StringBuilder(width);
        for (int i = digits.length(); i < width; i++) {
            out.append('0');
        }
        out.append(digits);
        if (signed) {
            int lastDigit = out.charAt(width - 1) - '0';
            boolean isZero = new BigInteger(out.toString()).signum() == 0;
            char sign = (negative && !isZero) ? NEGATIVE.charAt(lastDigit) : POSITIVE.charAt(lastDigit);
            out.setCharAt(width - 1, sign);
        }
        return out.toString();
    }
}
