package com.carddemo.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DisclosureGroupTest {

    private static final String RECORD =
            "A00000000001000100150{0000000000000000000000000000";

    @Test
    void copybookCVTRA02Y_parsesInterestRateOverpunch() {
        DisclosureGroup value = DisclosureGroup.fromRecord(RECORD);

        assertThat(value.groupId()).isEqualTo("A000000000");
        assertThat(value.typeCd()).isEqualTo("01");
        assertThat(value.catCd()).isEqualTo("0001");
        assertThat(value.intRate()).isEqualByComparingTo("15.00");
    }

    @Test
    void copybookCVTRA02Y_roundTripsAndAcceptsRecordTerminator() {
        DisclosureGroup value = DisclosureGroup.fromRecord(RECORD + "\n");

        assertThat(value.toRecord()).isEqualTo(RECORD.substring(0, 22) + " ".repeat(28));
        assertThat(value.toRecord()).hasSize(DisclosureGroup.RECORD_LENGTH);
    }

    @Test
    void copybookCVTRA02Y_supportsNegativeZeroAndMaximumRate() {
        String negative = RECORD.substring(0, 16) + "01234N" + RECORD.substring(22);
        assertThat(DisclosureGroup.fromRecord(negative).intRate()).isEqualByComparingTo("-123.45");
        String zero = RECORD.substring(0, 16) + "00000}" + RECORD.substring(22);
        assertThat(DisclosureGroup.fromRecord(zero).intRate()).isEqualByComparingTo("0.00");
        String maximum = RECORD.substring(0, 16) + "99999I" + RECORD.substring(22);
        assertThat(DisclosureGroup.fromRecord(maximum).intRate()).isEqualByComparingTo("9999.99");
        assertThatThrownBy(() -> new DisclosureGroup("A", "01", "0001", new BigDecimal("1.234")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copybookCVTRA02Y_rejectsWrongLength() {
        assertThatThrownBy(() -> DisclosureGroup.fromRecord(RECORD.substring(0, RECORD.length() - 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
