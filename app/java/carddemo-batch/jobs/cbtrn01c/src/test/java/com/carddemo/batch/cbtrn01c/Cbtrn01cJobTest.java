package com.carddemo.batch.cbtrn01c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.testkit.SchemaContract;
import java.nio.file.Path;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Cbtrn01cJobTest {

    static final Path MODULE_DIR = Path.of("").toAbsolutePath();

    @Test
    void registeredForServiceLoader() {
        assertThat(ServiceLoader.load(BatchJob.class).stream().map(ServiceLoader.Provider::get).map(BatchJob::programId))
                .contains("CBTRN01C");
        assertThat(new Cbtrn01cJob().programId()).isEqualTo("CBTRN01C");
    }

    @Test
    void dailyTranVerificationSchemaContract() throws Exception {
        SchemaContract.assertMatches(Cbtrn01cDatasets.DAILY_TRAN_VERIFICATION_SCHEMA, MODULE_DIR);
    }

    @Test
    void sysoutSchemaContract() throws Exception {
        SchemaContract.assertMatches(SysoutDataset.SCHEMA, MODULE_DIR);
    }

    /** Z-DISPLAY-IO-STATUS; the 9x/non-numeric rows are derived by hand from the paragraph (ASCII code points). */
    @ParameterizedTest
    @CsvSource({"35,0035", "04,0004", "23,0023", "9A,9065", "3Z,3090"})
    void ioStatus04(String status, String expected) {
        assertThat(Cbtrn01cJob.ioStatus04(status)).isEqualTo(expected);
    }
}
