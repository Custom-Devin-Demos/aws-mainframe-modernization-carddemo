package com.carddemo.batch.cbtrn01c;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobRunner;
import com.carddemo.batch.common.output.OutputLayout;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import com.carddemo.batch.testkit.SampleData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** CBTRN01C Java job vs. expected outputs derived from the original COBOL under GnuCOBOL (fixtures/README.md). */
class Cbtrn01cParityTest {

    static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    @TempDir
    Path out;

    static Map<String, Path> sampleInputs() {
        Map<String, Path> in = new LinkedHashMap<>();
        in.put(Cbtrn01cJob.DALYTRAN, SampleData.file("dailytran.txt"));
        in.put(Cbtrn01cJob.CUSTFILE, SampleData.file("custdata.txt"));
        in.put(Cbtrn01cJob.XREFFILE, SampleData.file("cardxref.txt"));
        in.put(Cbtrn01cJob.CARDFILE, SampleData.file("carddata.txt"));
        in.put(Cbtrn01cJob.ACCTFILE, SampleData.file("acctdata.txt"));
        in.put(Cbtrn01cJob.TRANFILE, FIXTURES.resolve("sample-data/input/transact.txt"));
        return in;
    }

    static Map<String, Path> scenarioInputs(String scenario) {
        Path dir = FIXTURES.resolve(scenario).resolve("input");
        Map<String, Path> in = new LinkedHashMap<>();
        in.put(Cbtrn01cJob.DALYTRAN, dir.resolve("dailytran.txt"));
        in.put(Cbtrn01cJob.CUSTFILE, dir.resolve("custdata.txt"));
        in.put(Cbtrn01cJob.XREFFILE, dir.resolve("cardxref.txt"));
        in.put(Cbtrn01cJob.CARDFILE, dir.resolve("carddata.txt"));
        in.put(Cbtrn01cJob.ACCTFILE, dir.resolve("acctdata.txt"));
        in.put(Cbtrn01cJob.TRANFILE, dir.resolve("transact.txt"));
        return in;
    }

    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of("sample-data", sampleInputs()),
                Arguments.of("xref-not-found", scenarioInputs("xref-not-found")),
                Arguments.of("account-not-found", scenarioInputs("account-not-found")),
                Arguments.of("empty-dalytran", scenarioInputs("empty-dalytran")),
                Arguments.of("mixed-outcomes", scenarioInputs("mixed-outcomes")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void matchesCobolOutputs(String scenario, Map<String, Path> inputs) throws Exception {
        JobHarness harness = JobHarness.forJob(new Cbtrn01cJob(), out);
        inputs.forEach(harness::input);
        JobHarness.Run run = harness.run();

        Path expected = FIXTURES.resolve(scenario).resolve("expected");
        ParityAssertions.assertParquetMatches(run.dataset(Cbtrn01cDatasets.DAILY_TRAN_VERIFICATION),
                expected.resolve("daily_tran_verification.csv"));
        ParityAssertions.assertCsvMatches(run.report(SysoutDataset.DATASET), expected.resolve("sysout.csv"));
        assertThat(run.sysout()).isEqualTo(Files.readString(expected.resolve("sysout.txt"), StandardCharsets.US_ASCII));

        Properties props = load(expected.resolve("result.properties"));
        assertThat(run.result().returnCode()).isEqualTo(Integer.parseInt(props.getProperty("return_code")));
        Map<String, Long> counters = new LinkedHashMap<>();
        props.stringPropertyNames().stream().filter(k -> k.startsWith("counter.")).forEach(
                k -> counters.put(k.substring("counter.".length()), Long.parseLong(props.getProperty(k))));
        assertThat(run.result().counters()).containsExactlyInAnyOrderEntriesOf(counters);

        assertThat(run.sysout()).startsWith("START OF EXECUTION OF PROGRAM CBTRN01C\n")
                .endsWith("END OF EXECUTION OF PROGRAM CBTRN01C\n");
        assertThat(Files.readString(run.manifest())).contains("\"return_code\" : 0");
        assertThat(run.schema(Cbtrn01cDatasets.DAILY_TRAN_VERIFICATION)).exists();
        assertThat(run.schema(SysoutDataset.DATASET)).exists();
    }

    @Test
    void sampleDataVerifiesEveryTransactionAndRepeatsTheLastLookupAtEof() throws Exception {
        JobHarness harness = JobHarness.forJob(new Cbtrn01cJob(), out);
        sampleInputs().forEach(harness::input);
        JobHarness.Run run = harness.run();

        assertThat(run.result().counters()).containsEntry("records_read", 300L).containsEntry("lookups", 301L)
                .containsEntry("verified", 301L).containsEntry("eof_reprocessed", 1L);
        assertThat(run.sysout()).doesNotContain("COULD NOT BE VERIFIED").doesNotContain("NOT FOUND");
        assertThat(run.sysout()).endsWith("""
                SUCCESSFUL READ OF XREF
                CARD NUMBER: 3260763612337560
                ACCOUNT ID : 00000000010
                CUSTOMER ID: 000000010
                SUCCESSFUL READ OF ACCOUNT FILE
                SUCCESSFUL READ OF XREF
                CARD NUMBER: 3260763612337560
                ACCOUNT ID : 00000000010
                CUSTOMER ID: 000000010
                SUCCESSFUL READ OF ACCOUNT FILE
                END OF EXECUTION OF PROGRAM CBTRN01C
                """);
    }

    @Test
    void keySysoutLinesForFailedLookups() throws Exception {
        JobHarness harness = JobHarness.forJob(new Cbtrn01cJob(), out);
        scenarioInputs("mixed-outcomes").forEach(harness::input);
        String sysout = harness.run().sysout();

        assertThat(sysout).contains("INVALID CARD NUMBER FOR XREF\n"
                + "CARD NUMBER 9999999999999999 COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-0000000001774260\n");
        assertThat(sysout).contains("INVALID ACCOUNT NUMBER FOUND\nACCOUNT 00000000005 NOT FOUND\n");
    }

    @Test
    void lastRecordNotInXrefIsReportedTwice() throws Exception {
        JobHarness harness = JobHarness.forJob(new Cbtrn01cJob(), out);
        scenarioInputs("xref-not-found").forEach(harness::input);
        String sysout = harness.run().sysout();

        String skip = "CARD NUMBER 0000000000000000 COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-0000000006292564\n";
        assertThat(sysout).contains(skip + "INVALID CARD NUMBER FOR XREF\n" + skip + "END OF EXECUTION");
    }

    @Test
    void emptyDalytranLooksUpTheInitialRecordOnce() throws Exception {
        JobHarness harness = JobHarness.forJob(new Cbtrn01cJob(), out);
        scenarioInputs("empty-dalytran").forEach(harness::input);
        JobHarness.Run run = harness.run();

        assertThat(run.sysout()).isEqualTo("START OF EXECUTION OF PROGRAM CBTRN01C\n"
                + "INVALID CARD NUMBER FOR XREF\n"
                + "CARD NUMBER " + " ".repeat(16) + " COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-" + " ".repeat(16) + "\n"
                + "END OF EXECUTION OF PROGRAM CBTRN01C\n");
        assertThat(run.result().counters()).containsEntry("records_read", 0L).containsEntry("lookups", 1L);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DALYTRAN", "CUSTFILE", "XREFFILE", "CARDFILE", "ACCTFILE", "TRANFILE"})
    void openFailureAbends(String missingDd) throws Exception {
        Map<String, Path> inputs = scenarioInputs("mixed-outcomes");
        inputs.remove(missingDd);
        Path expected = FIXTURES.resolve("open-failure").resolve(missingDd).resolve("expected");
        assertAbendMatches(inputs, expected);
    }

    @Test
    void missingFileForSuppliedDdIsAlsoStatus35() throws Exception {
        Map<String, Path> inputs = scenarioInputs("mixed-outcomes");
        inputs.put(Cbtrn01cJob.XREFFILE, out.resolve("does-not-exist.txt"));
        assertAbendMatches(inputs, FIXTURES.resolve("open-failure/XREFFILE/expected"));
    }

    @Test
    void readErrorAbendsAfterTheGoodRecords() throws Exception {
        Map<String, Path> inputs = scenarioInputs("mixed-outcomes");
        inputs.put(Cbtrn01cJob.DALYTRAN, FIXTURES.resolve("read-error/input/dailytran.txt"));
        assertAbendMatches(inputs, FIXTURES.resolve("read-error/expected"));
    }

    private void assertAbendMatches(Map<String, Path> inputs, Path expected) throws IOException {
        ByteArrayOutputStream sysout = new ByteArrayOutputStream();
        JobContext.Builder b = JobContext.builder(Cbtrn01cJob.PROGRAM_ID).runDate(JobHarness.RUN_DATE).runId(JobHarness.RUN_ID)
                .clock(Clock.fixed(JobHarness.CLOCK, ZoneOffset.UTC)).stagingDir(out)
                .sysout(new PrintStream(sysout, true, StandardCharsets.UTF_8));
        inputs.forEach(b::input);
        JobContext ctx = b.build();

        Properties props = load(expected.resolve("result.properties"));
        assertThatThrownBy(() -> JobRunner.run(new Cbtrn01cJob(), ctx))
                .isInstanceOfSatisfying(AbendException.class,
                        e -> assertThat(e.abendCode()).isEqualTo(Integer.parseInt(props.getProperty("abend_code"))));
        assertThat(sysout.toString(StandardCharsets.UTF_8))
                .isEqualTo(Files.readString(expected.resolve("sysout.txt"), StandardCharsets.US_ASCII));
        assertThat(out.resolve(OutputLayout.manifestFile(Cbtrn01cJob.PROGRAM_ID, JobHarness.RUN_DATE, JobHarness.RUN_ID)))
                .doesNotExist();
        assertThat(ctx.outputs()).isEmpty();
    }

    static Properties load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.US_ASCII)) {
            p.load(r);
        }
        return p;
    }
}
