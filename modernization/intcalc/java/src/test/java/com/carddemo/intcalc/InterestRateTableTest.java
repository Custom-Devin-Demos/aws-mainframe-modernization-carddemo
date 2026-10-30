package com.carddemo.intcalc;

import static com.carddemo.intcalc.TestData.rate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rules R2/R3: disclosure-group lookup with DEFAULT fallback (status '23'). */
class InterestRateTableTest {

    private final InterestRateTable table = new InterestRateTable(List.of(
            rate("GOLD", "01", 1, "19.99"),
            rate("GOLD", "02", 1, "0.00"),
            rate("DEFAULT", "01", 1, "15.00"),
            rate("DEFAULT", "02", 1, "9.99"),
            rate("DEFAULT", "03", 1, "12.34")));

    @Test
    void exactKeyHitUsesGroupRate() {
        assertEquals(new BigDecimal("19.99"), table.rateFor("GOLD      ", "01", "0001"));
    }

    @Test
    void groupIdIsComparedSpacePadded() {
        assertEquals(new BigDecimal("19.99"), table.rateFor("GOLD", "01", "0001"));
    }

    @Test
    void missingKeyFallsBackToDefaultGroup() {
        assertEquals(new BigDecimal("12.34"), table.rateFor("GOLD      ", "03", "0001"));
        assertEquals(new BigDecimal("15.00"), table.rateFor("NOGROUP   ", "01", "0001"));
    }

    @Test
    void explicitZeroRateIsAHitAndDoesNotFallBack() {
        assertEquals(new BigDecimal("0.00"), table.rateFor("GOLD      ", "02", "0001"));
    }

    @Test
    void missingDefaultRowAbends() {
        assertThrows(IntCalcException.class, () -> table.rateFor("GOLD      ", "09", "0001"));
    }

    @Test
    void duplicateKeysAreRejected() {
        assertThrows(IntCalcException.class, () -> new InterestRateTable(List.of(
                rate("GOLD", "01", 1, "1.00"), rate("GOLD", "01", 1, "2.00"))));
    }
}
