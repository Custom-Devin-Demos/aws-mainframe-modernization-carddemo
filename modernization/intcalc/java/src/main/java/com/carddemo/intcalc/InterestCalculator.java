package com.carddemo.intcalc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Paragraph 1300-COMPUTE-INTEREST. */
public final class InterestCalculator {

    private static final BigDecimal ANNUAL_PERCENT_TO_MONTHLY = BigDecimal.valueOf(1200);

    private InterestCalculator() {
    }

    /**
     * {@code COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200} into
     * {@code PIC S9(09)V99}: scale 2, truncated toward zero (no ROUNDED).
     */
    public static BigDecimal monthlyInterest(BigDecimal categoryBalance, BigDecimal annualRatePercent) {
        BigDecimal raw = categoryBalance.multiply(annualRatePercent)
                .divide(ANNUAL_PERCENT_TO_MONTHLY, 2, RoundingMode.DOWN);
        return CobolNumeric.fitPic(raw, 9, 2);
    }
}
