package com.carddemo.interest.calc;

import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Pure interest arithmetic from CBACT04C 1300-COMPUTE-INTEREST.
 *
 * <pre>
 * COMPUTE WS-MONTHLY-INT = ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200
 * ADD WS-MONTHLY-INT TO WS-TOTAL-INT
 * </pre>
 *
 * WS-MONTHLY-INT and WS-TOTAL-INT are PIC S9(09)V99. COMPUTE without ROUNDED truncates,
 * so results must be scale 2 with {@link java.math.RoundingMode#DOWN}.
 */
@Component
@Trace(program = "CBACT04C", paragraph = "1300-COMPUTE-INTEREST", lines = "462-470")
public class InterestCalculator {

    /** Divisor from CBACT04C line 465. */
    public static final BigDecimal MONTHLY_DIVISOR = BigDecimal.valueOf(1200);

    /** Scale of WS-MONTHLY-INT / WS-TOTAL-INT (S9(09)V99). */
    public static final int SCALE = 2;

    /**
     * Monthly interest for one category balance.
     *
     * @param balance TRAN-CAT-BAL (S9(09)V99)
     * @param annualRate DIS-INT-RATE (S9(04)V99), percentage
     * @return WS-MONTHLY-INT, scale 2, truncated (RoundingMode.DOWN)
     */
    @Trace(program = "CBACT04C", paragraph = "1300-COMPUTE-INTEREST", lines = "464-465")
    public BigDecimal computeMonthlyInterest(BigDecimal balance, BigDecimal annualRate) {
        throw new UnsupportedOperationException("Child B: implement 1300-COMPUTE-INTEREST");
    }

    /**
     * Adds a monthly interest amount to the running per-account total (ADD WS-MONTHLY-INT TO WS-TOTAL-INT).
     *
     * @return the new total, scale 2
     */
    @Trace(program = "CBACT04C", paragraph = "1300-COMPUTE-INTEREST", lines = "467")
    public BigDecimal accumulate(BigDecimal runningTotal, BigDecimal monthlyInterest) {
        throw new UnsupportedOperationException("Child B: implement ADD WS-MONTHLY-INT TO WS-TOTAL-INT");
    }
}
