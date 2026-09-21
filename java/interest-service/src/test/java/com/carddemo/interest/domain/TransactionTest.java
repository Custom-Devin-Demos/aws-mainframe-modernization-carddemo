package com.carddemo.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final String RECORD = "0000000000683580"
            + "01"
            + "0001"
            + "POS TERM  "
            + "Purchase at Abshire-Lowe" + " ".repeat(76)
            + "0000005047G"
            + "800000000"
            + "Abshire-Lowe" + " ".repeat(38)
            + "North Enoshaven" + " ".repeat(35)
            + "72112     "
            + "4859452612877065"
            + "2022-06-10 19:27:53.000000"
            + " ".repeat(26)
            + " ".repeat(20);

    @Test
    void copybookCVTRA05Y_parsesDailyTransaction() {
        Transaction value = Transaction.fromRecord(RECORD);

        assertThat(value.id()).isEqualTo("0000000000683580");
        assertThat(value.typeCd()).isEqualTo("01");
        assertThat(value.catCd()).isEqualTo("0001");
        assertThat(value.amount()).isEqualByComparingTo("504.77");
        assertThat(value.merchantId()).isEqualTo("800000000");
        assertThat(value.merchantName()).isEqualTo("Abshire-Lowe");
        assertThat(value.merchantCity()).isEqualTo("North Enoshaven");
        assertThat(value.procTs()).isEmpty();
    }

    @Test
    void copybookCVTRA05Y_roundTripsAndAcceptsRecordTerminator() {
        Transaction value = Transaction.fromRecord(RECORD + "\r\n");

        assertThat(value.toRecord()).isEqualTo(RECORD);
        assertThat(value.toRecord()).hasSize(Transaction.RECORD_LENGTH);
    }

    @Test
    void copybookCVTRA05Y_supportsNegativeZeroAndMaximumAmount() {
        String negative = RECORD.substring(0, 132) + "0000001234N" + RECORD.substring(143);
        assertThat(Transaction.fromRecord(negative).amount()).isEqualByComparingTo("-123.45");
        String zero = RECORD.substring(0, 132) + "0000000000}" + RECORD.substring(143);
        assertThat(Transaction.fromRecord(zero).amount()).isEqualByComparingTo("0.00");
        String maximum = RECORD.substring(0, 132) + "9999999999I" + RECORD.substring(143);
        assertThat(Transaction.fromRecord(maximum).amount()).isEqualByComparingTo("999999999.99");
        assertThatThrownBy(() -> new Transaction("id", "01", "0001", "", "", new BigDecimal("1.234"),
                "000000000", "", "", "", "", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copybookCVTRA05Y_rejectsWrongLength() {
        assertThatThrownBy(() -> Transaction.fromRecord(RECORD.substring(0, RECORD.length() - 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
