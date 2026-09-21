package com.carddemo.interest.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

/** Validates CBACT04C 1300-COMPUTE-INTEREST (lines 462-470). */
class InterestCalculatorTest {

    private final InterestCalculator calc = new InterestCalculator();

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
        assertThat(actual.scale()).isEqualTo(2);
    }

    @Test
    void paragraph1300ComputeInterest_truncatesInsteadOfRounding() {
        BigDecimal result = calc.computeMonthlyInterest(bd("1000.00"), bd("12.50"));
        assertMoney(result, "10.41");
        BigDecimal halfUp = bd("1000.00").multiply(bd("12.50")).divide(bd("1200"), 2, RoundingMode.HALF_UP);
        assertThat(halfUp).isEqualByComparingTo("10.42");
        assertThat(result).isNotEqualByComparingTo(halfUp);
    }

    @Test
    void paragraph1300ComputeInterest_halfUpWouldRoundUpButTruncationDoesNot() {
        // 100.00 * 19.99 / 1200 = 1.66583.. -> 1.66 (HALF_UP would give 1.67)
        assertMoney(calc.computeMonthlyInterest(bd("100.00"), bd("19.99")), "1.66");
        // 999.99 * 5.00 / 1200 = 4.166625 -> 4.16 (HALF_UP -> 4.17)
        assertMoney(calc.computeMonthlyInterest(bd("999.99"), bd("5.00")), "4.16");
    }

    @Test
    void paragraph1300ComputeInterest_negativeBalanceTruncatesTowardZero() {
        assertMoney(calc.computeMonthlyInterest(bd("-1000.00"), bd("12.50")), "-10.41");
        // FLOOR would give -0.01; COBOL truncation gives -0.00
        assertMoney(calc.computeMonthlyInterest(bd("-0.50"), bd("12.00")), "0.00");
    }

    @Test
    void paragraph1300ComputeInterest_zeroRateGivesZeroAtScale2() {
        assertMoney(calc.computeMonthlyInterest(bd("1000.00"), bd("0.00")), "0.00");
    }

    @Test
    void paragraph1300ComputeInterest_zeroBalanceGivesZeroAtScale2() {
        assertMoney(calc.computeMonthlyInterest(bd("0.00"), bd("12.50")), "0.00");
    }

    @Test
    void paragraph1300ComputeInterest_tinyBalanceTruncatesToZero() {
        // 0.01 * 12.50 / 1200 = 0.000104.. -> 0.00
        assertMoney(calc.computeMonthlyInterest(bd("0.01"), bd("12.50")), "0.00");
    }

    @Test
    void paragraph1300ComputeInterest_maxMagnitudeOverflowsIntoS9V99() {
        // 999999999.99 * 9999.99 / 1200 = 8333324999.9166.. -> exact 8333324999.91;
        // WS-MONTHLY-INT is S9(09)V99 so the high-order digit is dropped: 333324999.91
        BigDecimal exact = bd("999999999.99").multiply(bd("9999.99")).divide(bd("1200"), 2, RoundingMode.DOWN);
        assertThat(exact).isEqualByComparingTo("8333324999.91");
        assertMoney(calc.computeMonthlyInterest(bd("999999999.99"), bd("9999.99")), "333324999.91");
    }

    @Test
    void paragraph1300ComputeInterest_largeInRangeValueKeepsAllDigits() {
        // 999999999.99 * 1.00 / 1200 = 833333.333325 -> 833333.33
        assertMoney(calc.computeMonthlyInterest(bd("999999999.99"), bd("1.00")), "833333.33");
    }

    @Test
    void paragraph1300ComputeInterest_resultScaleAlwaysTwo() {
        assertThat(calc.computeMonthlyInterest(bd("1200"), bd("12")).scale()).isEqualTo(2);
        assertThat(calc.computeMonthlyInterest(bd("1200.000"), bd("12.0000")).scale()).isEqualTo(2);
        assertThat(calc.computeMonthlyInterest(bd("1"), bd("1")).scale()).isEqualTo(2);
    }

    @Test
    void paragraph1300ComputeInterest_nullInputsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> calc.computeMonthlyInterest(null, bd("1.00")))
                .withMessageContaining("balance");
        assertThatNullPointerException()
                .isThrownBy(() -> calc.computeMonthlyInterest(bd("1.00"), null))
                .withMessageContaining("annualRate");
    }

    @Test
    void paragraph1300AddToTotal_keepsScale2() {
        assertMoney(calc.accumulate(bd("0.00"), bd("10.41")), "10.41");
        assertMoney(calc.accumulate(bd("10.41"), bd("5.25")), "15.66");
        assertMoney(calc.accumulate(bd("1"), bd("2")), "3.00");
    }

    @Test
    void paragraph1300AddToTotal_handlesNegativeTotals() {
        assertMoney(calc.accumulate(bd("-10.41"), bd("-5.25")), "-15.66");
        assertMoney(calc.accumulate(bd("10.00"), bd("-15.50")), "-5.50");
        assertMoney(calc.accumulate(bd("-10.00"), bd("10.00")), "0.00");
    }

    @Test
    void paragraph1300AddToTotal_highOrderTruncationOnOverflow() {
        // 999999999.99 + 0.01 = 1000000000.00 -> 000000000.00 in S9(09)V99
        assertMoney(calc.accumulate(bd("999999999.99"), bd("0.01")), "0.00");
        // 999999999.99 + 1.50 = 1000000001.49 -> 000000001.49
        assertMoney(calc.accumulate(bd("999999999.99"), bd("1.50")), "1.49");
        // negative: -999999999.99 + -2.25 = -1000000002.24 -> -000000002.24
        assertMoney(calc.accumulate(bd("-999999999.99"), bd("-2.25")), "-2.24");
        // just under the limit is untouched
        assertMoney(calc.accumulate(bd("999999998.99"), bd("1.00")), "999999999.99");
    }

    @Test
    void paragraph1300AddToTotal_nullInputsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> calc.accumulate(null, bd("1.00")))
                .withMessageContaining("runningTotal");
        assertThatNullPointerException()
                .isThrownBy(() -> calc.accumulate(bd("1.00"), null))
                .withMessageContaining("monthlyInterest");
    }
}
