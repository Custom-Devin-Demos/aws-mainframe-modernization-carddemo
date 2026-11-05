package com.carddemo.online.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CsutldtcDateValidatorTest {

    @ParameterizedTest
    @CsvSource({
        "2024-02-29, 0000",
        "2023-02-29, 2508",
        "2023-04-31, 2508",
        "2023-01-00, 2508",
        "2023-13-01, 2517",
        "2023-00-01, 2517",
        "0000-01-01, 2521",
        "1582-10-14, 2513",
        "1582-10-15, 0000",
        "20x3-01-01, 2520",
        "2023/01/01, 2520",
    })
    void ceedaysFeedback(String date, String msgNum) {
        assertThat(CsutldtcDateValidator.check(date)).isEqualTo(msgNum);
        boolean accepted = msgNum.equals("0000") || msgNum.equals("2513");
        assertThat(CsutldtcDateValidator.acceptedByCorpt00c(date)).isEqualTo(accepted);
    }
}
