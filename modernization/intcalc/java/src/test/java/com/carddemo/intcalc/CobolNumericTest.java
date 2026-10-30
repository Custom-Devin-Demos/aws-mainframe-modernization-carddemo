package com.carddemo.intcalc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Rule R11: fixed-point storage of money in COBOL zoned-decimal fields. */
class CobolNumericTest {

    @Test
    void parsesEbcdicStyleOverpunch() {
        assertEquals(new BigDecimal("1234.56"), CobolNumeric.parseSigned("0000012345F", 2));
        assertEquals(new BigDecimal("-250.75"), CobolNumeric.parseSigned("0000002507N", 2));
        assertEquals(new BigDecimal("0.00"), CobolNumeric.parseSigned("0000000000{", 2));
        assertEquals(new BigDecimal("-0.10"), CobolNumeric.parseSigned("0000000001}", 2));
    }

    @Test
    void parsesGnuCobolAsciiSignAndUnsignedDigits() {
        assertEquals(new BigDecimal("-1.23"), CobolNumeric.parseSigned("12s", 2));
        assertEquals(new BigDecimal("1.23"), CobolNumeric.parseSigned("123", 2));
    }

    @Test
    void rejectsNonNumericFields() {
        assertThrows(IllegalArgumentException.class, () -> CobolNumeric.parseSigned("00A0000000{", 2));
        assertThrows(IllegalArgumentException.class, () -> CobolNumeric.parseSigned("0000000000*", 2));
    }

    @Test
    void formatsSignedFieldsAndWritesNegativeZeroAsPositive() {
        assertEquals("0000000205F", CobolNumeric.formatSigned(new BigDecimal("20.56"), 9, 2));
        assertEquals("0000000031L", CobolNumeric.formatSigned(new BigDecimal("-3.13"), 9, 2));
        assertEquals("0000000000{", CobolNumeric.formatSigned(new BigDecimal("-0.00"), 9, 2));
        assertEquals("10228815843B", CobolNumeric.formatSigned(new BigDecimal("1022881584.32"), 10, 2));
    }

    @Test
    void fitPicTruncatesDecimalsTowardZero() {
        assertEquals(new BigDecimal("1.99"), CobolNumeric.fitPic(new BigDecimal("1.999"), 9, 2));
        assertEquals(new BigDecimal("-1.99"), CobolNumeric.fitPic(new BigDecimal("-1.999"), 9, 2));
    }

    @Test
    void fitPicDropsHighOrderDigitsLikeCobolWithoutSizeError() {
        assertEquals(new BigDecimal("234567890.12"),
                CobolNumeric.fitPic(new BigDecimal("1234567890.12"), 9, 2));
        assertEquals(new BigDecimal("-234567890.12"),
                CobolNumeric.fitPic(new BigDecimal("-1234567890.12"), 9, 2));
    }

    @Test
    void formatsUnsignedKeepingLowOrderDigits() {
        assertEquals("000042", CobolNumeric.formatUnsigned(42, 6));
        assertEquals("000000", CobolNumeric.formatUnsigned(1_000_000, 6));
    }
}
