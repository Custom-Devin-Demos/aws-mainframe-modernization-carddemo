package com.carddemo.batch.cbact03c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.job.BatchLauncher;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.output.OutputLayout;
import com.carddemo.batch.testkit.DatasetFiles;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.SampleData;
import com.carddemo.batch.testkit.SchemaContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Cbact03cJobTest {

    @TempDir
    Path tmp;

    static Stream<Arguments> displayIoStatusGolden() {
        List<Map<String, String>> rows = DatasetFiles.readCsv(Fixtures.expected("display-io-status", "display-io-status.csv"));
        return rows.stream().map(r -> Arguments.of(r.get("io_status"), r.get("line_text")));
    }

    @ParameterizedTest(name = "status {0}")
    @MethodSource("displayIoStatusGolden")
    void displayIoStatusMatchesCobolParagraph(String ioStatus, String expectedLine) {
        assertThat(Cbact03cJob.ioStatusLine(ioStatus)).isEqualTo(expectedLine);
    }

    @Test
    void schemaContracts() throws Exception {
        SchemaContract.assertMatches(Cbact03cJob.xrefListingSchema(), Fixtures.MODULE_DIR);
        SchemaContract.assertMatches(Cbact03cJob.sysoutSchema(), Fixtures.MODULE_DIR);
    }

    @Test
    void publishedSchemasEqualContracts() throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbact03cJob(), tmp)
                .input(Cbact03cJob.XREFFILE_DD, SampleData.file("cardxref.txt")).run();
        assertThat(Files.readString(run.schema(Cbact03cJob.XREF_LISTING))).isEqualTo(Cbact03cJob.xrefListingSchema().toSparkJson());
        assertThat(Files.readString(run.schema(Cbact03cJob.SYSOUT))).isEqualTo(Cbact03cJob.sysoutSchema().toSparkJson());
        assertThat(run.context().outputs()).extracting(o -> o.dataset() + ":" + o.format() + ":" + o.rows())
                .containsExactlyInAnyOrder("sysout:CSV:102", "xref_listing:PARQUET:50");
    }

    @Test
    void launcherFindsJobAndRunsIt() {
        assertThat(ServiceLoader.load(BatchJob.class).stream().map(ServiceLoader.Provider::type))
                .contains(Cbact03cJob.class);
        Path out = tmp.resolve("launch");
        int exit = BatchLauncher.launch(new String[] {
            "--input", "XREFFILE=" + SampleData.file("cardxref.txt"),
            "--output-root", out.toString(), "--run-date", "2022-07-06", "--run-id", "local"});
        assertThat(exit).isZero();
        assertThat(out.resolve(OutputLayout.dataFile("CBACT03C", "xref_listing", LocalDate.of(2022, 7, 6), "local", OutputFormat.PARQUET))).exists();
        assertThat(out.resolve(OutputLayout.dataFile("CBACT03C", "sysout", LocalDate.of(2022, 7, 6), "local", OutputFormat.CSV))).exists();
    }

    @Test
    void launcherExitsWithAbendStatusWhenXreffileIsMissing() {
        int exit = BatchLauncher.launch(new String[] {
            "--input", "XREFFILE=" + tmp.resolve("missing.txt"), "--output-root", tmp.resolve("launch").toString()});
        assertThat(exit).isEqualTo(BatchLauncher.ABEND_EXIT);
    }

    @Test
    void deployTemplatesDescribeReadxref() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode jobDef = json.readTree(Fixtures.MODULE_DIR.resolve("deploy/cbact03c-job-definition.json").toFile());
        assertThat(jobDef.path("jobDefinitionName").asText()).isEqualTo("carddemo-cbact03c");
        assertThat(jobDef.path("containerProperties").path("image").asText()).contains("carddemo-batch-cbact03c");

        JsonNode asl = json.readTree(Fixtures.MODULE_DIR.resolve("deploy/READXREF.asl.json").toFile());
        assertThat(asl.path("StartAt").asText()).isEqualTo("STEP05");
        JsonNode step = asl.path("States").path("STEP05");
        assertThat(step.path("Resource").asText()).isEqualTo("arn:aws:states:::batch:submitJob.sync");
        assertThat(step.path("Parameters").path("JobDefinition").asText()).isEqualTo("carddemo-cbact03c");
        assertThat(step.path("Parameters").path("ContainerOverrides").path("Command.$").asText()).contains("XREFFILE=");
    }
}
