package com.carddemo.batch.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.common.record.ZonedDecimal;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedDecimalTest {

    @Test
    void decodesEbcdicStyleOverpunch() {
        assertThat(ZonedDecimal.decode("00000001940{", 2, true)).isEqualByComparingTo("194.00");
        assertThat(ZonedDecimal.decode("0000005047G", 2, true)).isEqualByComparingTo("504.77");
        assertThat(ZonedDecimal.decode("0000009190}", 2, true)).isEqualByComparingTo("-919.00");
        assertThat(ZonedDecimal.decode("0000000123R", 2, true)).isEqualByComparingTo("-12.39");
    }

    @Test
    void decodesGnuCobolAsciiNegativeOverpunch() {
        assertThat(ZonedDecimal.decode("0000000123y", 2, true)).isEqualByComparingTo("-12.39");
    }

    @Test
    void decodesUnsignedDigits() {
        assertThat(ZonedDecimal.decode("000012345", 2, true)).isEqualByComparingTo("123.45");
    }

    @Test
    void encodeRoundTrips() {
        for (String v : new String[] {"0.00", "194.00", "-919.00", "-12.39", "99999999.99"}) {
            String enc = ZonedDecimal.encode(new BigDecimal(v), 9, 2, true);
            assertThat(enc).hasSize(11);
            assertThat(ZonedDecimal.decode(enc, 2, true)).isEqualByComparingTo(v);
        }
        assertThat(ZonedDecimal.encode(new BigDecimal("-919.00"), 9, 2, true)).isEqualTo("0000009190}");
        assertThat(ZonedDecimal.encode(BigDecimal.ZERO, 10, 2, true)).isEqualTo("00000000000{");
    }

    @Test
    void encodeTruncatesLikeCobolMove() {
        assertThat(ZonedDecimal.encode(new BigDecimal("12345.678"), 3, 2, true)).isEqualTo("3456G");
        assertThat(ZonedDecimal.encode(new BigDecimal("-5.00"), 3, 2, false)).isEqualTo("00500");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> ZonedDecimal.decode("12 4", 0, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
