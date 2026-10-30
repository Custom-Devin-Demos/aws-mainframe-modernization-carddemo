package com.carddemo.batch.cbact01c;

import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.schema.DatasetSchema;
import com.carddemo.batch.testkit.SchemaContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class Cbact01cJobTest {

    @Test
    void registeredAsServiceWithProgramId() {
        assertThat(ServiceLoader.load(BatchJob.class).stream().map(ServiceLoader.Provider::get).map(BatchJob::programId))
                .contains("CBACT01C");
        assertThat(new Cbact01cJob().programId()).isEqualTo("CBACT01C");
    }

    @Test
    void schemaContractsMatchCheckedInFiles() throws IOException {
        for (DatasetSchema schema : Cbact01cSchemas.ALL) {
            SchemaContract.assertMatches(schema, Path.of("").toAbsolutePath());
        }
    }

    /** 9910-DISPLAY-IO-STATUS, derived by hand from the paragraph (ASCII code of IO-STAT2 for 9x). */
    @ParameterizedTest(name = "IO-STATUS [{0}] -> [{1}]")
    @CsvSource({"35,0035", "30,0030", "10,0010", "00,0000", "9A,9065", "'9 ',9032", "3X,3088"})
    void displayIoStatus(String status, String shown) {
        assertThat(Cbact01cJob.ioStatus04(status)).isEqualTo(shown);
    }
}
