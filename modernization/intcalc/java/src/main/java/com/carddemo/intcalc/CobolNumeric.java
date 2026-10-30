package com.carddemo.intcalc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Fixed-point helpers that reproduce COBOL DISPLAY-numeric semantics.
 *
 * <p>Signed zoned decimals use the trailing overpunch found in the CardDemo ASCII extracts
 * ({@code '{'}/{@code 'A'-'I'} for +0..+9, {@code '}'}/{@code 'J'-'R'} for -0..-9), which is
 * also what GnuCOBOL writes with {@code -fsign=EBCDIC}.
 */
public final class CobolNumeric {

    private static final String POSITIVE = "{ABCDEFGHI";
    private static final String NEGATIVE = "}JKLMNOPQR";

    private CobolNumeric() {
    }

    /** Parses a signed zoned field such as {@code PIC S9(09)V99}. */
    public static BigDecimal parseSigned(String field, int scale) {
        if (field.isEmpty()) {
            throw new IllegalArgumentException("empty numeric field");
        }
        char last = field.charAt(field.length() - 1);
        boolean negative;
        int lastDigit;
        if (POSITIVE.indexOf(last) >= 0) {
            negative = false;
            lastDigit = POSITIVE.indexOf(last);
        } else if (NEGATIVE.indexOf(last) >= 0) {
            negative = true;
            lastDigit = NEGATIVE.indexOf(last);
        } else if (last >= 'p' && last <= 'y') {
            negative = true;
            lastDigit = last - 'p';
        } else if (Character.isDigit(last)) {
            negative = false;
            lastDigit = last - '0';
        } else {
            throw new IllegalArgumentException("invalid sign digit '" + last + "' in '" + field + "'");
        }
        String digits = field.substring(0, field.length() - 1) + lastDigit;
        if (!digits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("non-numeric field '" + field + "'");
        }
        BigDecimal value = new BigDecimal(new BigInteger(digits), scale);
        return negative ? value.negate() : value;
    }

    /** Parses an unsigned zoned field such as {@code PIC 9(04)}. */
    public static long parseUnsigned(String field) {
        if (field.isEmpty() || !field.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("non-numeric field '" + field + "'");
        }
        return Long.parseLong(field);
    }

    /**
     * Stores {@code value} into a {@code PIC S9(intDigits)V9(scale)} receiver the way a COBOL
     * MOVE/COMPUTE/ADD without ROUNDED or ON SIZE ERROR does: excess decimals are truncated
     * toward zero and excess high-order integer digits are dropped.
     */
    public static BigDecimal fitPic(BigDecimal value, int intDigits, int scale) {
        BigDecimal truncated = value.setScale(scale, RoundingMode.DOWN);
        BigInteger unscaled = truncated.unscaledValue();
        BigInteger modulus = BigInteger.TEN.pow(intDigits + scale);
        BigInteger magnitude = unscaled.abs().mod(modulus);
        return new BigDecimal(unscaled.signum() < 0 ? magnitude.negate() : magnitude, scale);
    }

    /** Formats {@code value} as a signed zoned field of {@code intDigits + scale} characters. */
    public static String formatSigned(BigDecimal value, int intDigits, int scale) {
        BigDecimal fitted = fitPic(value, intDigits, scale);
        int width = intDigits + scale;
        String digits = String.format("%0" + width + "d", fitted.unscaledValue().abs());
        int lastDigit = digits.charAt(width - 1) - '0';
        char sign = fitted.signum() < 0 ? NEGATIVE.charAt(lastDigit) : POSITIVE.charAt(lastDigit);
        return digits.substring(0, width - 1) + sign;
    }

    /** Formats a non-negative integer as an unsigned zoned field, keeping the low-order digits. */
    public static String formatUnsigned(long value, int width) {
        String digits = Long.toString(Math.abs(value));
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        }
        return "0".repeat(width - digits.length()) + digits;
    }
}
