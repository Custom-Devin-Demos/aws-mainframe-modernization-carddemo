package com.carddemo.batch.cobtupdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import com.carddemo.batch.testkit.SampleData;
import com.carddemo.batch.testkit.SchemaContract;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CobtupdtParityTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
    private static final Path MODULE_DIR = Path.of("").toAbsolutePath();

    @TempDir
    Path out;

    /** Runs a fixture scenario; TRANTYPE/TRANCATG default to the real sample files when the scenario has none. */
    private JobHarness.Run runScenario(String scenario) throws Exception {
        Path input = FIXTURES.resolve(scenario).resolve("input");
        return JobHarness.forJob(new CobtupdtJob(), out)
                .input("INPFILE", input.resolve("INPFILE.txt"))
                .input("TRANTYPE", orSample(input.resolve("TRANTYPE.txt"), "trantype.txt"))
                .input("TRANCATG", orSample(input.resolve("TRANCATG.txt"), "trancatg.txt"))
                .run();
    }

    private static Path orSample(Path scenarioFile, String sampleName) {
        return Files.exists(scenarioFile) ? scenarioFile : SampleData.file(sampleName);
    }

    private static void assertMatchesExpected(JobHarness.Run run, String scenario) throws IOException {
        Path expected = FIXTURES.resolve(scenario).resolve("expected");
        ParityAssertions.assertParquetMatches(run.dataset("transaction_type"), expected.resolve("transaction_type.csv"));
        ParityAssertions.assertCsvMatches(run.report("maintenance_log"), expected.resolve("maintenance_log.csv"));
        assertThat(sysoutLines(run)).as("SYSOUT of %s", scenario)
                .containsExactlyElementsOf(Files.readAllLines(expected.resolve("sysout.txt"), StandardCharsets.US_ASCII));

        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(expected.resolve("run.properties"))) {
            p.load(r);
        }
        assertThat(run.result().returnCode()).as("RETURN-CODE").isEqualTo(Integer.parseInt(p.getProperty("return_code")));
        Map<String, Long> counters = new LinkedHashMap<>();
        p.stringPropertyNames().stream().filter(k -> k.startsWith("counter.")).sorted()
                .forEach(k -> counters.put(k.substring("counter.".length()), Long.parseLong(p.getProperty(k))));
        assertThat(run.result().counters()).isEqualTo(counters);
        assertThat(run.manifest()).exists();
        assertThat(run.schema("transaction_type")).exists();
        assertThat(run.schema("maintenance_log")).exists();
    }

    private static List<String> sysoutLines(JobHarness.Run run) {
        return run.sysout().lines().map(CobtupdtJob::rtrim).toList();
    }

    @Test
    void sampleDataScenarioMatchesCobolDerivedFixtures() throws Exception {
        assertThat(FIXTURES.resolve("sample-data/input/TRANTYPE.txt")).doesNotExist();
        JobHarness.Run run = JobHarness.forJob(new CobtupdtJob(), out)
                .input("INPFILE", FIXTURES.resolve("sample-data/input/INPFILE.txt"))
                .input("TRANTYPE", SampleData.file("trantype.txt"))
                .input("TRANCATG", SampleData.file("trancatg.txt"))
                .run();
        assertMatchesExpected(run, "sample-data");
        assertThat(run.result().returnCode()).isEqualTo(4);
        assertThat(sysoutLines(run)).contains(
                "OPEN FILE OK",
                "Error accessing: TRANSACTION_TYPE table. SQLCODE: -803",
                "Error accessing: TRANSACTION_TYPE table. SQLCODE: -532",
                "RECORD DELETED SUCCESSFULLY");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"all-success-rc0", "message-carry-over", "invalid-and-blank-records", "ebcdic-collation", "empty-input"})
    void edgeCaseScenarioMatchesCobolDerivedFixtures(String scenario) throws Exception {
        assertMatchesExpected(runScenario(scenario), scenario);
    }

    @Test
    void processingContinuesAfter9999AbendAndRcStaysFour() throws Exception {
        JobHarness.Run run = runScenario("message-carry-over");
        assertThat(run.result().returnCode()).isEqualTo(4);
        List<String> lines = sysoutLines(run);
        assertThat(lines.get(lines.size() - 1)).isEqualTo("No records found.TRANSACTION_TYPE table. SQLCODE: -803");
        assertThat(lines).contains("PROCESSING   A05Success after errors keeps RC 4", "RECORD INSERTED SUCCESSFULLY",
                "ERROR: TYPE NOT VALIDSACTION_TYPE table. SQLCODE: -532");
        assertThat(run.result().counters()).containsEntry("records_read", 6L).containsEntry("records_inserted", 1L);
    }

    @Test
    void successfulRunKeepsReturnCodeZero() throws Exception {
        JobHarness.Run run = runScenario("all-success-rc0");
        assertThat(run.result().returnCode()).isZero();
        assertThat(run.result().counters()).containsEntry("errors", 0L);
    }

    @Test
    void emptyInpfileOnlyOpensAndPublishesUnchangedSnapshot() throws Exception {
        JobHarness.Run run = runScenario("empty-input");
        assertThat(sysoutLines(run)).containsExactly("OPEN FILE OK");
        assertThat(run.result().counters()).containsEntry("records_read", 0L).containsEntry("table_rows", 3L);
    }

    @Test
    void unreadableInpfileAbendsWithoutOutputs() {
        JobHarness harness = JobHarness.forJob(new CobtupdtJob(), out)
                .input("INPFILE", out.resolve("missing.txt"))
                .input("TRANTYPE", SampleData.file("trantype.txt"))
                .input("TRANCATG", SampleData.file("trancatg.txt"));
        assertThatThrownBy(harness::run).isInstanceOf(AbendException.class)
                .satisfies(e -> assertThat(((AbendException) e).abendCode()).isEqualTo(CobtupdtJob.ABEND_OPEN_FAILED));
    }

    @Test
    void sqlcodeIsEditedAsPicMinusMinusMinusMinusNine() {
        assertThat(CobtupdtJob.editSqlcode(-803)).isEqualTo(" -803");
        assertThat(CobtupdtJob.editSqlcode(-532)).isEqualTo(" -532");
        assertThat(CobtupdtJob.editSqlcode(100)).isEqualTo("  100");
        assertThat(CobtupdtJob.editSqlcode(0)).isEqualTo("    0");
        assertThat(CobtupdtJob.editSqlcode(-9999)).isEqualTo("-9999");
    }

    @Test
    void jobIsRegisteredAsService() {
        assertThat(ServiceLoader.load(BatchJob.class).stream().map(ServiceLoader.Provider::get).map(BatchJob::programId))
                .contains("COBTUPDT");
    }

    @Test
    void schemaContractsAreCheckedIn() throws IOException {
        SchemaContract.assertMatches(CobtupdtJob.TRANSACTION_TYPE_SCHEMA, MODULE_DIR);
        SchemaContract.assertMatches(CobtupdtJob.MAINTENANCE_LOG_SCHEMA, MODULE_DIR);
    }

}
