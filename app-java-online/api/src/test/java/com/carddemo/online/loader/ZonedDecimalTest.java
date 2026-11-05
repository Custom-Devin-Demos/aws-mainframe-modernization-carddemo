package com.carddemo.online.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class ZonedDecimalTest {

    @ParameterizedTest
    @CsvSource({
        "00000001940{, 194.00",
        "0000000504G,  5.047",
        "00000000000{, 0.00",
        "0000000100}, -1.000",
        "0000000123J, -12.31",
        "0000000123R, -12.39",
        "0000000123I, 12.39",
        "000000012345, 123.45"
    })
    void decodesOverpunchedSign(String raw, String expected) {
        int scale = new BigDecimal(expected).scale();
        assertThat(ZonedDecimal.parse(raw, scale)).isEqualByComparingTo(expected);
    }

    @Test
    void blankIsZero() {
        assertThat(ZonedDecimal.parse("      ", 2)).isEqualByComparingTo("0");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> ZonedDecimal.parse("12X", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
