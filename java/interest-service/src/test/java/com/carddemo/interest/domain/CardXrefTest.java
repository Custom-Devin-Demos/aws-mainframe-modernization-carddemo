package com.carddemo.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CardXrefTest {

    private static final String RECORD =
            "050002445376574000000005000000000050" + " ".repeat(14);

    @Test
    void copybookCVACT03Y_parsesCardAndAccountIdentifiers() {
        CardXref value = CardXref.fromRecord(RECORD);

        assertThat(value.cardNum()).isEqualTo("0500024453765740");
        assertThat(value.custId()).isEqualTo("000000050");
        assertThat(value.acctId()).isEqualTo("00000000050");
    }

    @Test
    void copybookCVACT03Y_roundTripsAndAcceptsRecordTerminator() {
        CardXref value = CardXref.fromRecord(RECORD + "\r");

        assertThat(value.toRecord()).isEqualTo(RECORD);
        assertThat(value.toRecord()).hasSize(CardXref.RECORD_LENGTH);
    }

    @Test
    void copybookCVACT03Y_rejectsWrongLengthAndNonDigits() {
        assertThatThrownBy(() -> CardXref.fromRecord(RECORD.substring(0, RECORD.length() - 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CardXref.fromRecord(RECORD.substring(0, 16) + "00000005A"
                + RECORD.substring(25)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
