package com.carddemo.batch.cbtrn02c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.job.BatchJob;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Cbtrn02cJobTest {

    @Test
    void registeredWithServiceLoader() {
        assertThat(ServiceLoader.load(BatchJob.class).stream().map(ServiceLoader.Provider::get)
                .filter(j -> j.programId().equals("CBTRN02C")).findFirst()).get().isInstanceOf(Cbtrn02cJob.class);
    }

    @ParameterizedTest
    @CsvSource({
            "2022-07-06T00:00:00Z, 2022-07-06-00.00.00.000000",
            "2022-07-06T13:45:12.347Z, 2022-07-06-13.45.12.340000",
            "1999-12-31T23:59:59.999Z, 1999-12-31-23.59.59.990000"})
    void db2Timestamp(String instant, String expected) {
        assertThat(Cbtrn02cJob.db2FormatTimestamp(Instant.parse(instant))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "10000000000.00, 0.00",
            "1000000051.00, 51.00",
            "9999999998.99, 999999998.99",
            "-10000000001.25, -1.25",
            "123.45, 123.45"})
    void wsTempBalPictureTruncation(String value, String expected) {
        assertThat(Cbtrn02cJob.picture(new BigDecimal(value), 9, 2)).isEqualByComparingTo(expected);
    }
}
