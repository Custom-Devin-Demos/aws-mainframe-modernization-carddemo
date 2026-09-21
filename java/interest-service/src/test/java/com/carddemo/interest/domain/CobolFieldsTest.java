package com.carddemo.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CobolFieldsTest {

    @Test
    void picX_trimsTrailingSpacesOnly() {
        assertThat(CobolFields.picX("  value  ", 0, 9)).isEqualTo("  value");
    }

    @Test
    void pic9_preservesLeadingZerosAndRejectsNonDigits() {
        assertThat(CobolFields.pic9("0012", 0, 4)).isEqualTo("0012");
        assertThatThrownBy(() -> CobolFields.pic9("00A2", 0, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void picS9V99_decodesPositiveNegativeAndZeroOverpunch() {
        assertThat(CobolFields.picS9V99("0000005047G", 0, 11))
                .isEqualByComparingTo("504.77");
        assertThat(CobolFields.picS9V99("0000001234N", 0, 11))
                .isEqualByComparingTo("-123.45");
        assertThat(CobolFields.picS9V99("0000000000}", 0, 11))
                .isEqualByComparingTo("0.00");
        assertThat(CobolFields.picS9V99("9999999999I", 0, 11))
                .isEqualByComparingTo("999999999.99");
        assertThatThrownBy(() -> CobolFields.picS9V99("0000000000X", 0, 11))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatS9V99_encodesOverpunchAndRejectsOverflow() {
        assertThat(CobolFields.formatS9V99(new BigDecimal("504.77"), 11))
                .isEqualTo("0000005047G");
        assertThat(CobolFields.formatS9V99(new BigDecimal("-123.45"), 11))
                .isEqualTo("0000001234N");
        assertThat(CobolFields.formatS9V99(new BigDecimal("0.00"), 11))
                .isEqualTo("0000000000{");
        assertThatThrownBy(() -> CobolFields.formatS9V99(new BigDecimal("1000000000.00"), 11))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatXAndFormat9_padAndValidateValues() {
        assertThat(CobolFields.formatX(null, 3)).isEqualTo("   ");
        assertThat(CobolFields.formatX("ab", 3)).isEqualTo("ab ");
        assertThat(CobolFields.format9("12", 4)).isEqualTo("0012");
        assertThatThrownBy(() -> CobolFields.format9("1A", 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CobolFields.format9("123", 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkLength_toleratesOneRecordTerminator() {
        assertThat(CobolFields.checkLength("abc\r\n", 3)).isEqualTo("abc");
        assertThat(CobolFields.checkLength("abc\n", 3)).isEqualTo("abc");
        assertThat(CobolFields.checkLength("abc\r", 3)).isEqualTo("abc");
        assertThatThrownBy(() -> CobolFields.checkLength("ab", 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scale2_rejectsNullAndRounding() {
        assertThat(CobolFields.scale2(new BigDecimal("1.20"), "amount"))
                .isEqualByComparingTo("1.20");
        assertThatThrownBy(() -> CobolFields.scale2(new BigDecimal("1.234"), "amount"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
        assertThatThrownBy(() -> CobolFields.scale2(null, "amount"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }
}
