package com.carddemo.creastmt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CobolTest {

    @Test
    void zonedDecimalOverpunch() {
        assertEquals(new BigDecimal("194.00"), Cobol.zoned("00000001940{", 2));
        assertEquals(new BigDecimal("504.77"), Cobol.zoned("0000005047G", 2));
        assertEquals(new BigDecimal("-47.88"), Cobol.zoned("0000000478Q", 2));
        assertEquals(new BigDecimal("-0.00"), Cobol.zoned("0000000000}", 2));
        assertEquals(new BigDecimal("12.34"), Cobol.zoned("00000001234", 2));
    }

    @Test
    void numericEditedPictures() {
        assertEquals("000000492.00 ", Cobol.editZeroFilled(new BigDecimal("492.00"), 9));
        assertEquals("000000492.00-", Cobol.editZeroFilled(new BigDecimal("-492.00"), 9));
        // S9(10)V99 into 9(9): high-order digit is dropped
        assertEquals("234567890.12 ", Cobol.editZeroFilled(new BigDecimal("1234567890.12"), 9));
        assertEquals("      183.88 ", Cobol.editZeroSuppressed(new BigDecimal("183.88"), 9));
        assertEquals("       47.88-", Cobol.editZeroSuppressed(new BigDecimal("-47.88"), 9));
        assertEquals("         .00 ", Cobol.editZeroSuppressed(BigDecimal.ZERO, 9));
        assertEquals("         .05 ", Cobol.editZeroSuppressed(new BigDecimal("-0.00").add(new BigDecimal("0.05")), 9));
    }

    @Test
    void stringDelimitedBy() {
        assertEquals("Ward Henri Jones ", Cobol.stringInto(17,
                Cobol.delimitedBy("Ward  ", " "), " ", Cobol.delimitedBy("Henri ", " "), " ",
                Cobol.delimitedBy("Jones", " "), " "));
        assertEquals("North", Cobol.delimitedBy("North Enoshaven   ", " "));
        assertEquals("abc", Cobol.stringInto(3, "ab", "cd"));
        assertEquals("ab  ", Cobol.stringInto(4, "ab"));
    }

    @Test
    void outrecRekeysByCardThenTransactionId() {
        String raw = Cobol.pic("T".repeat(16) + "x".repeat(246), 262) + "C".repeat(16) + "Z".repeat(72);
        String rekeyed = TransactionTable.rekey(raw);
        assertEquals(350, rekeyed.length());
        assertEquals("C".repeat(16) + "T".repeat(16), rekeyed.substring(0, 32));
        // OUTREC keeps 279..328 and blanks 329..350
        assertEquals("Z".repeat(50) + " ".repeat(22), rekeyed.substring(278));
    }
}
