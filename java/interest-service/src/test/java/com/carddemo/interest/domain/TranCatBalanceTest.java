package com.carddemo.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TranCatBalanceTest {

    private static final String RECORD =
            "000000000010100010000000000{0000000000000000000000";

    @Test
    void copybookCVTRA01Y_parsesSignedBalanceOverpunch() {
        TranCatBalance value = TranCatBalance.fromRecord(RECORD);

        assertThat(value.acctId()).isEqualTo("00000000001");
        assertThat(value.typeCd()).isEqualTo("01");
        assertThat(value.catCd()).isEqualTo("0001");
        assertThat(value.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void copybookCVTRA01Y_roundTripsAndAcceptsRecordTerminator() {
        TranCatBalance value = TranCatBalance.fromRecord(RECORD + "\r\n");

        assertThat(value.toRecord()).isEqualTo(RECORD.substring(0, 28) + " ".repeat(22));
        assertThat(value.toRecord()).hasSize(TranCatBalance.RECORD_LENGTH);
    }

    @Test
    void copybookCVTRA01Y_supportsNegativeAndMaximumBalance() {
        assertThat(TranCatBalance.fromRecord(RECORD.substring(0, 17) + "0000001234N" + RECORD.substring(28))
                .balance()).isEqualByComparingTo("-123.45");
        assertThat(TranCatBalance.fromRecord(RECORD.substring(0, 17) + "9999999999I" + RECORD.substring(28))
                .balance()).isEqualByComparingTo("999999999.99");
        assertThatThrownBy(() -> new TranCatBalance("1", "01", "0001", new BigDecimal("1.234")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copybookCVTRA01Y_rejectsWrongLength() {
        assertThatThrownBy(() -> TranCatBalance.fromRecord(RECORD.substring(0, RECORD.length() - 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
