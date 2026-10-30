package com.carddemo.batch.cbtrn03c;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** BR-CBTRN03C-10/11: edited pictures and S9(09)V99 truncation (values observed under GnuCOBOL 3.1.2). */
class ReportLinesTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "0.00          |'               '|'               '",
        "0.05          |'            .05'|'+           .05'",
        "-0.05         |'-           .05'|'-           .05'",
        "1.00          |'           1.00'|'+          1.00'",
        "-1.00         |'-          1.00'|'-          1.00'",
        "999.99        |'         999.99'|'+        999.99'",
        "1000.00       |'       1,000.00'|'+      1,000.00'",
        "-1234567.89   |'-  1,234,567.89'|'-  1,234,567.89'",
        "999999999.99  |' 999,999,999.99'|'+999,999,999.99'",
        "-999999999.99 |'-999,999,999.99'|'-999,999,999.99'",
        "12.30         |'          12.30'|'+         12.30'",
        "-100000.00    |'-    100,000.00'|'-    100,000.00'"})
    void editedPictures(String value, String minusPicture, String plusPicture) {
        assertThat(ReportLines.edit(new BigDecimal(value), false)).isEqualTo(minusPicture);
        assertThat(ReportLines.edit(new BigDecimal(value), true)).isEqualTo(plusPicture);
    }

    @Test
    void totalsLoseHighOrderDigits() {
        assertThat(ReportLines.toPicture(new BigDecimal("1000000004.99"))).isEqualByComparingTo("4.99");
        assertThat(ReportLines.toPicture(new BigDecimal("-1000000000.01"))).isEqualByComparingTo("-0.01");
    }

    @Test
    void lineLayoutsMatchCvtra07y() {
        assertThat(ReportLines.HEADER_1).hasSize(114);
        assertThat(ReportLines.nameHeader("2022-01-01", "2022-07-06")).hasSize(115);
        assertThat(ReportLines.pageTotal(BigDecimal.ONE)).hasSize(112);
        assertThat(ReportLines.accountTotal(BigDecimal.ONE)).hasSize(112);
        assertThat(ReportLines.grandTotal(BigDecimal.ONE)).hasSize(112);
        assertThat(ReportLines.detail(new ReportLines.Detail("1", "2", "01", "x", "0001", "y", "POS", BigDecimal.ONE))).hasSize(114);
    }
}
