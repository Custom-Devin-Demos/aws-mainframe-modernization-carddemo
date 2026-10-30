package com.carddemo.batch.cbtrn02c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.schema.DatasetSchema;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.SampleData;
import com.carddemo.batch.testkit.SchemaContract;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Cbtrn02cSchemaTest {

    static Stream<DatasetSchema> schemas() {
        return Stream.of(Cbtrn02cSchemas.postedTransactions(), Cbtrn02cSchemas.dailyRejects(),
                Cbtrn02cSchemas.accountMaster(), Cbtrn02cSchemas.tranCatBalance());
    }

    @ParameterizedTest
    @MethodSource("schemas")
    void checkedInContractMatches(DatasetSchema schema) throws Exception {
        SchemaContract.assertMatches(schema, Path.of("").toAbsolutePath());
    }

    @Test
    void publishedSchemasEqualContracts(@TempDir Path out) throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbtrn02cJob(), out)
                .input("DALYTRAN", SampleData.file("dailytran.txt"))
                .input("XREFFILE", SampleData.file("cardxref.txt"))
                .input("ACCTFILE", SampleData.file("acctdata.txt"))
                .input("TCATBALF", SampleData.file("tcatbal.txt"))
                .run();
        for (DatasetSchema s : schemas().toList()) {
            assertThat(Files.readString(run.schema(s.name()), StandardCharsets.UTF_8)).isEqualTo(s.toSparkJson());
        }
    }

    @Test
    void dailyRejectsAppendsTrailerColumns() {
        assertThat(Cbtrn02cSchemas.dailyRejects().columns()).last()
                .satisfies(c -> assertThat(c.name()).isEqualTo(Cbtrn02cSchemas.VALIDATION_FAIL_REASON_DESC));
    }
}
