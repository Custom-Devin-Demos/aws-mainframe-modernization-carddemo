package com.carddemo.interest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class Db2TimestampSupplierTest {

    @Test
    void paragraphZGetDb2FormatTimestamp_formatsAsDb2TimestampWith26Chars() {
        Clock clock = Clock.fixed(Instant.parse("2022-07-18T10:15:30.129Z"), ZoneOffset.UTC);
        String ts = new Db2TimestampSupplier(clock).get();

        assertThat(ts).isEqualTo("2022-07-18-10.15.30.120000");
        assertThat(ts).hasSize(26);
    }
}
