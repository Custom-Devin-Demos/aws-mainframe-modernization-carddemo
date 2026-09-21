package com.carddemo.interest.calc;

import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
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

    /** Number of integer digits in S9(09)V99. */
    public static final int INTEGER_DIGITS = 9;

    /** 10^9: modulus applied to the integer part on high-order truncation. */
    public static final BigDecimal INTEGER_MODULUS = BigDecimal.TEN.pow(INTEGER_DIGITS);

    /**
     * Monthly interest for one category balance.
     *
     * @param balance TRAN-CAT-BAL (S9(09)V99)
     * @param annualRate DIS-INT-RATE (S9(04)V99), percentage
     * @return WS-MONTHLY-INT, scale 2, truncated toward zero (RoundingMode.DOWN)
     */
    @Trace(program = "CBACT04C", paragraph = "1300-COMPUTE-INTEREST", lines = "464-465")
    public BigDecimal computeMonthlyInterest(BigDecimal balance, BigDecimal annualRate) {
        Objects.requireNonNull(balance, "balance (TRAN-CAT-BAL) must not be null");
        Objects.requireNonNull(annualRate, "annualRate (DIS-INT-RATE) must not be null");
        return truncateHighOrder(balance.multiply(annualRate).divide(MONTHLY_DIVISOR, SCALE, RoundingMode.DOWN));
    }

    /**
     * Adds a monthly interest amount to the running per-account total (ADD WS-MONTHLY-INT TO WS-TOTAL-INT).
     *
     * <p>WS-TOTAL-INT is PIC S9(09)V99. The COBOL ADD has no ON SIZE ERROR clause, so when the sum
     * exceeds nine integer digits the high-order digits are silently dropped (the field keeps the
     * low-order nine integer digits and its sign). This method reproduces that behaviour: the integer
     * part is reduced modulo 10^9 with the sign of the true sum preserved, the two decimal digits are
     * kept unchanged.
     *
     * @return the new total, scale 2
     */
    @Trace(program = "CBACT04C", paragraph = "1300-COMPUTE-INTEREST", lines = "467")
    public BigDecimal accumulate(BigDecimal runningTotal, BigDecimal monthlyInterest) {
        Objects.requireNonNull(runningTotal, "runningTotal (WS-TOTAL-INT) must not be null");
        Objects.requireNonNull(monthlyInterest, "monthlyInterest (WS-MONTHLY-INT) must not be null");
        return truncateHighOrder(runningTotal.add(monthlyInterest).setScale(SCALE, RoundingMode.DOWN));
    }

    /**
     * Drops integer digits beyond the ninth, mirroring a COBOL size overflow into a S9(09)V99 field.
     * The value must already have scale {@link #SCALE}.
     */
    static BigDecimal truncateHighOrder(BigDecimal value) {
        BigDecimal abs = value.abs();
        if (abs.compareTo(INTEGER_MODULUS) < 0) {
            return value;
        }
        BigDecimal truncated = abs.remainder(INTEGER_MODULUS).setScale(SCALE, RoundingMode.DOWN);
        return value.signum() < 0 ? truncated.negate() : truncated;
    }
}
