package com.carddemo.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final String RECORD = "00000000001Y00000001940{00000020200{00000010200{"
            + "2014-11-202025-05-202025-05-2000000000000{00000000000{A000000000"
            + " ".repeat(188);

    @Test
    void copybookCVACT01Y_parsesAccountBalances() {
        Account value = Account.fromRecord(RECORD);

        assertThat(value.id()).isEqualTo("00000000001");
        assertThat(value.activeStatus()).isEqualTo("Y");
        assertThat(value.currBal()).isEqualByComparingTo("194.00");
        assertThat(value.creditLimit()).isEqualByComparingTo("2020.00");
        assertThat(value.cashCreditLimit()).isEqualByComparingTo("1020.00");
        assertThat(value.openDate()).isEqualTo("2014-11-20");
        assertThat(value.addrZip()).isEqualTo("A000000000");
        assertThat(value.groupId()).isEmpty();
    }

    @Test
    void copybookCVACT01Y_roundTripsAndAcceptsRecordTerminator() {
        Account value = Account.fromRecord(RECORD + "\r\n");

        assertThat(value.toRecord()).isEqualTo(RECORD);
        assertThat(value.toRecord()).hasSize(Account.RECORD_LENGTH);
    }

    @Test
    void copybookCVACT01Y_supportsNegativeZeroAndMaximumBalance() {
        String negative = RECORD.substring(0, 12) + "00000001234N" + RECORD.substring(24);
        assertThat(Account.fromRecord(negative).currBal()).isEqualByComparingTo("-123.45");
        String zero = RECORD.substring(0, 12) + "00000000000}" + RECORD.substring(24);
        assertThat(Account.fromRecord(zero).currBal()).isEqualByComparingTo("0.00");
        String maximum = RECORD.substring(0, 12) + "99999999999I" + RECORD.substring(24);
        assertThat(Account.fromRecord(maximum).currBal()).isEqualByComparingTo("9999999999.99");
        assertThatThrownBy(() -> new Account("1", "Y", new BigDecimal("1.234"),
                BigDecimal.ZERO, BigDecimal.ZERO, "", "", "", BigDecimal.ZERO, BigDecimal.ZERO, "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copybookCVACT01Y_rejectsWrongLength() {
        assertThatThrownBy(() -> Account.fromRecord(RECORD.substring(0, RECORD.length() - 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
