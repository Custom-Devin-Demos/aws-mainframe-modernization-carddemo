package com.carddemo.intcalc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Rule R4: monthly interest = balance * rate / 1200, scale 2, RoundingMode.DOWN. */
class InterestCalculatorTest {

    @ParameterizedTest(name = "{0} @ {1}% -> {2}")
    @CsvSource({
            "1234.56, 19.99, 20.56",      // 20.5654...
            "999.99, 24.99, 20.82",       // 20.8247...
            "500.00, 21.37, 8.90",        // 8.9041...
            "-250.75, 15.00, -3.13",      // -3.1343... truncated toward zero, not floor
            "-0.06, 21.37, 0.00",         // -0.00106 -> zero
            "0.48, 24.99, 0.00",          // 0.009996 -> zero (still emitted by the job)
            "1200.00, 7.77, 7.77",
            "999999999.99, 24.99, 20824999.99",
            "123456789.01, 19.99, 2056584.34",
            "100.00, -12.00, -1.00"       // negative rates are allowed by PIC S9(04)V99
    })
    void truncatesToTwoDecimals(String balance, String rate, String expected) {
        BigDecimal actual = InterestCalculator.monthlyInterest(new BigDecimal(balance), new BigDecimal(rate));
        assertEquals(new BigDecimal(expected), actual);
        assertEquals(2, actual.scale());
    }

    @ParameterizedTest
    @CsvSource({"999999999.99, 9999.99, 333324999.91"})
    void dropsHighOrderDigitsOnOverflowOfS9_09_V99(String balance, String rate, String expected) {
        // 8,333,324,999.91... does not fit 9 integer digits; COBOL keeps the low-order 9
        // (verified against GnuCOBOL 3.1.2 COMPUTE into PIC S9(09)V99).
        assertEquals(new BigDecimal(expected),
                InterestCalculator.monthlyInterest(new BigDecimal(balance), new BigDecimal(rate)));
    }
}
