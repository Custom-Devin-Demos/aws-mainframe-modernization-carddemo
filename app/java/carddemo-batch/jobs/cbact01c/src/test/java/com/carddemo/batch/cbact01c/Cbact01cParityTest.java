package com.carddemo.batch.cbact01c;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobResult;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.schema.DatasetSchema;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import com.carddemo.batch.testkit.SampleData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Parity of the Java job with the COBOL program. Expected files come from GnuCOBOL runs of
 * app/cbl/CBACT01C.cbl (tools/cobol); see src/test/resources/fixtures/README.md.
 */
class Cbact01cParityTest {
    static final Path FIXTURES = Path.of("src/test/resources/fixtures").toAbsolutePath();
    static final List<DatasetSchema> DATASETS = List.of(Cbact01cSchemas.ACCOUNT_OUT, Cbact01cSchemas.ACCOUNT_ARRAY,
            Cbact01cSchemas.ACCOUNT_VBRC);

    @TempDir
    Path out;

    @Test
    void sampleData() throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbact01cJob(), out)
                .input(Cbact01cJob.ACCTFILE, SampleData.file("acctdata.txt"))
                .run();

        assertSuccessfulRun("sample-data", run);
        List<String> lines = run.sysout().lines().toList();
        assertThat(lines).hasSize(752);
        assertThat(lines.get(0)).isEqualTo("START OF EXECUTION OF PROGRAM CBACT01C");
        assertThat(lines.subList(1, 16)).startsWith("ACCT-ID                 :00000000001", "ACCT-ACTIVE-STATUS      :Y",
                "ACCT-CURR-BAL           :00000001940{");
        assertThat(lines).contains("VBRC-REC1:00000000001Y", "VBRC-REC2:0000000000100000001940{00000020200{2025",
                "ACCT-GROUP-ID           :          ");
        assertThat(lines.get(751)).isEqualTo("END OF EXECUTION OF PROGRAM CBACT01C");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cyc-debit-carryover", "field-values-and-key-order", "reissue-date-formats", "empty-acctfile"})
    void edgeCase(String scenario) throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbact01cJob(), out)
                .input(Cbact01cJob.ACCTFILE, FIXTURES.resolve(scenario).resolve("input/acctdata.txt"))
                .run();

        assertSuccessfulRun(scenario, run);
    }

    @ParameterizedTest
    @ValueSource(strings = {"acctfile-missing", "outfile-open-error", "arryfile-open-error", "vbrcfile-open-error",
            "acctfile-read-error"})
    void abend(String scenario) throws IOException {
        Path input = scenario.equals("acctfile-missing") ? out.resolve("no-such-acctfile.txt")
                : FIXTURES.resolve(scenario).resolve("input/acctdata.txt");
        switch (scenario) {
            case "outfile-open-error" -> blockDataset(Cbact01cSchemas.ACCOUNT_OUT);
            case "arryfile-open-error" -> blockDataset(Cbact01cSchemas.ACCOUNT_ARRAY);
            case "vbrcfile-open-error" -> blockDataset(Cbact01cSchemas.ACCOUNT_VBRC);
            default -> {
            }
        }
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        JobContext ctx = JobContext.builder(Cbact01cJob.PROGRAM_ID).runDate(JobHarness.RUN_DATE).runId(JobHarness.RUN_ID)
                .clock(Clock.fixed(JobHarness.CLOCK, ZoneOffset.UTC)).stagingDir(out)
                .sysout(new PrintStream(stdout, true, StandardCharsets.UTF_8))
                .input(Cbact01cJob.ACCTFILE, input).build();

        AbendException abend = catchThrowableOfType(() -> new Cbact01cJob().run(ctx), AbendException.class);

        Path expected = FIXTURES.resolve(scenario).resolve("expected");
        Properties result = load(expected.resolve("result.properties"));
        assertThat(abend).as("CEE3ABD").isNotNull();
        assertThat(abend.abendCode()).isEqualTo(Integer.parseInt(result.getProperty("abend_code")));
        ParityAssertions.assertCsvMatches(ctx.datasetFile("sysout", OutputFormat.CSV), expected.resolve("sysout.csv"));
        for (DatasetSchema ds : DATASETS) {
            Path csv = expected.resolve(ds.name() + ".csv");
            if (Files.exists(csv)) {
                ParityAssertions.assertParquetMatches(ctx.datasetFile(ds.name(), OutputFormat.PARQUET), csv);
            } else {
                assertThat(Files.exists(ctx.datasetFile(ds.name(), OutputFormat.PARQUET))).as("%s never opened", ds.name()).isFalse();
            }
        }
        assertThat(stdout.toString(StandardCharsets.UTF_8).lines()).endsWith("ABENDING PROGRAM");
        assertThat(out.resolve("curated/cbact01c/_runs")).as("no manifest after an abend").doesNotExist();
    }

    private void assertSuccessfulRun(String scenario, JobHarness.Run run) throws IOException {
        Path expected = FIXTURES.resolve(scenario).resolve("expected");
        Properties props = load(expected.resolve("result.properties"));
        JobResult result = run.result();
        assertThat(result.returnCode()).isEqualTo(Integer.parseInt(props.getProperty("return_code")));
        assertThat(result.counters()).isEqualTo(Map.of(
                Cbact01cJob.READ, Long.parseLong(props.getProperty("acctfile_records_read")),
                Cbact01cJob.OUT_WRITTEN, Long.parseLong(props.getProperty("outfile_records_written")),
                Cbact01cJob.ARRY_WRITTEN, Long.parseLong(props.getProperty("arryfile_records_written")),
                Cbact01cJob.VBRC_WRITTEN, Long.parseLong(props.getProperty("vbrcfile_records_written"))));
        for (DatasetSchema ds : DATASETS) {
            ParityAssertions.assertParquetMatches(run.dataset(ds.name()), expected.resolve(ds.name() + ".csv"));
            assertThat(run.schema(ds.name())).content(StandardCharsets.UTF_8).isEqualTo(ds.toSparkJson());
        }
        ParityAssertions.assertCsvMatches(run.report("sysout"), expected.resolve("sysout.csv"));
        assertThat(run.schema("sysout")).content(StandardCharsets.UTF_8).isEqualTo(Cbact01cSchemas.SYSOUT.toSparkJson());
        assertThat(run.manifest()).exists();
    }

    /** A regular file where the dataset's directory must go makes its OPEN OUTPUT fail. */
    private void blockDataset(DatasetSchema ds) throws IOException {
        Path blocker = out.resolve("curated/cbact01c").resolve(ds.name());
        Files.createDirectories(blocker.getParent());
        Files.createFile(blocker);
    }

    private static Properties load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.US_ASCII)) {
            p.load(r);
        }
        return p;
    }
}
