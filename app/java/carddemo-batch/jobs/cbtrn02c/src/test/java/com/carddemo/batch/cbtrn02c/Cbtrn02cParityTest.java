package com.carddemo.batch.cbtrn02c;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.JobResult;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import com.carddemo.batch.testkit.SampleData;
import com.carddemo.batch.common.job.JobContext;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Parity of the Java job with the original CBTRN02C. Every expected file was produced by the
 * unmodified COBOL program under GnuCOBOL (tools/cobol/run_cobol.sh); see fixtures/README.md.
 */
class Cbtrn02cParityTest {

    static final Path FIXTURES = Path.of("src/test/resources/fixtures");
    static final List<String> DATASETS = List.of(Cbtrn02cSchemas.POSTED_TRANSACTIONS, Cbtrn02cSchemas.DAILY_REJECTS,
            Cbtrn02cSchemas.ACCOUNT_MASTER, Cbtrn02cSchemas.TRAN_CAT_BALANCE);

    @TempDir
    Path out;

    @Test
    void sampleData() throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbtrn02cJob(), out)
                .input("DALYTRAN", SampleData.file("dailytran.txt"))
                .input("XREFFILE", SampleData.file("cardxref.txt"))
                .input("ACCTFILE", SampleData.file("acctdata.txt"))
                .input("TCATBALF", SampleData.file("tcatbal.txt"))
                .run();
        Path expected = FIXTURES.resolve("sample-data/expected");
        assertMatchesCobol(run, expected);
        assertThat(run.result().returnCode()).isEqualTo(4);
        assertThat(run.result().counters()).containsEntry(Cbtrn02cJob.COUNTER_PROCESSED, 300L)
                .containsEntry(Cbtrn02cJob.COUNTER_REJECTED, 38L);
        assertThat(lines(run.sysout())).contains("TRANSACTIONS PROCESSED :000000300", "TRANSACTIONS REJECTED  :000000038");
        assertThat(Files.readString(run.manifest())).containsPattern("\"return_code\"\\s*:\\s*4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"reject-reasons", "tcatbal-create-update", "negative-amounts", "picture-truncation"})
    void edgeCase(String scenario) throws Exception {
        JobHarness.Run run = runScenario(scenario);
        assertMatchesCobol(run, FIXTURES.resolve(scenario).resolve("expected"));
    }

    @Test
    void rejectReasonsCountersAndReturnCode() throws Exception {
        JobResult r = runScenario("reject-reasons").result();
        assertThat(r.returnCode()).isEqualTo(4);
        assertThat(r.counters()).containsEntry(Cbtrn02cJob.COUNTER_PROCESSED, 8L).containsEntry(Cbtrn02cJob.COUNTER_REJECTED, 5L);
    }

    @Test
    void noRejectsGivesReturnCodeZero() throws Exception {
        JobResult r = runScenario("tcatbal-create-update").result();
        assertThat(r.returnCode()).isZero();
        assertThat(r.counters()).containsEntry(Cbtrn02cJob.COUNTER_PROCESSED, 5L).containsEntry(Cbtrn02cJob.COUNTER_REJECTED, 0L);
    }

    @Test
    void duplicateTranIdAbends() throws Exception {
        Path scenario = FIXTURES.resolve("duplicate-tran-id-abend");
        assertThatThrownBy(harness(scenario.resolve("input"))::run).isInstanceOf(AbendException.class)
                .satisfies(e -> assertThat(((AbendException) e).abendCode()).isEqualTo(999));
        // The COBOL run ends with the CEE3ABD stub's own line, which the Java job does not print.
        List<String> expected = Files.readAllLines(scenario.resolve("expected/sysout.txt")).stream()
                .filter(l -> !l.startsWith("CEE3ABD ")).toList();
        assertThat(expected).endsWith("ERROR WRITING TO TRANSACTION FILE", "FILE STATUS IS: NNNN0022", "ABENDING PROGRAM");
        assertThat(Files.readString(scenario.resolve("expected/return_code.txt")).trim()).isEqualTo("99");
        assertThat(lines(sysoutOf(scenario.resolve("input"), true))).containsExactlyElementsOf(expected);
    }

    @Test
    void missingAccountFileAbendsAtOpen() {
        // 0400-ACCTFILE-OPEN with file status 35 (file not found); derived by hand from the COBOL text.
        String sysout = sysoutOf(FIXTURES.resolve("reject-reasons/input"), false);
        assertThat(lines(sysout)).containsExactly("START OF EXECUTION OF PROGRAM CBTRN02C",
                "ERROR OPENING ACCOUNT MASTER FILE", "FILE STATUS IS: NNNN0035", "ABENDING PROGRAM");
    }

    private String sysoutOf(Path input, boolean withAcctfile) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        JobContext.Builder b = JobContext.builder(Cbtrn02cJob.PROGRAM_ID)
                .stagingDir(out.resolve("direct-run")).clock(Clock.fixed(JobHarness.CLOCK, ZoneOffset.UTC))
                .sysout(new PrintStream(buf, true, StandardCharsets.UTF_8))
                .input("DALYTRAN", input.resolve("dailytran.txt"))
                .input("XREFFILE", input.resolve("cardxref.txt"))
                .input("TCATBALF", input.resolve("tcatbal.txt"));
        if (withAcctfile) {
            b.input("ACCTFILE", input.resolve("acctdata.txt"));
        }
        JobContext ctx = b.build();
        assertThatThrownBy(() -> new Cbtrn02cJob().run(ctx)).isInstanceOf(AbendException.class)
                .satisfies(e -> assertThat(((AbendException) e).abendCode()).isEqualTo(999));
        return buf.toString(StandardCharsets.UTF_8);
    }

    private JobHarness harness(Path input) {
        return JobHarness.forJob(new Cbtrn02cJob(), out)
                .input("DALYTRAN", input.resolve("dailytran.txt"))
                .input("XREFFILE", input.resolve("cardxref.txt"))
                .input("ACCTFILE", input.resolve("acctdata.txt"))
                .input("TCATBALF", input.resolve("tcatbal.txt"));
    }

    private JobHarness.Run runScenario(String scenario) throws Exception {
        return harness(FIXTURES.resolve(scenario).resolve("input")).run();
    }

    /** All four datasets, the return code, both counters and the full SYSOUT against the COBOL run. */
    private static void assertMatchesCobol(JobHarness.Run run, Path expected) throws Exception {
        for (String ds : DATASETS) {
            ParityAssertions.assertParquetMatches(run.dataset(ds), expected.resolve(ds + ".csv"));
        }
        List<String> cobolSysout = Files.readAllLines(expected.resolve("sysout.txt"), StandardCharsets.UTF_8);
        assertThat(lines(run.sysout())).containsExactlyElementsOf(cobolSysout);
        int rc = Integer.parseInt(Files.readString(expected.resolve("return_code.txt")).trim());
        assertThat(run.result().returnCode()).isEqualTo(rc);
        assertThat(run.result().counters().get(Cbtrn02cJob.COUNTER_PROCESSED))
                .isEqualTo(counterFrom(cobolSysout, "TRANSACTIONS PROCESSED :"));
        assertThat(run.result().counters().get(Cbtrn02cJob.COUNTER_REJECTED))
                .isEqualTo(counterFrom(cobolSysout, "TRANSACTIONS REJECTED  :"));
    }

    private static long counterFrom(List<String> sysout, String prefix) {
        return sysout.stream().filter(l -> l.startsWith(prefix)).map(l -> Long.parseLong(l.substring(prefix.length())))
                .findFirst().orElseThrow();
    }

    static List<String> lines(String text) {
        return text.isEmpty() ? List.of() : Arrays.asList(text.split("\r?\n"));
    }
}
