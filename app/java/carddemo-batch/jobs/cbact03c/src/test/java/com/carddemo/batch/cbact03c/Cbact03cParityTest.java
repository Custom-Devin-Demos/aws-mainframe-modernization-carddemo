package com.carddemo.batch.cbact03c;

import static com.carddemo.batch.cbact03c.Cbact03cJob.COUNTER_SYSOUT_LINES;
import static com.carddemo.batch.cbact03c.Cbact03cJob.COUNTER_XREF_RECORDS_READ;
import static com.carddemo.batch.cbact03c.Cbact03cJob.SYSOUT;
import static com.carddemo.batch.cbact03c.Cbact03cJob.XREFFILE_DD;
import static com.carddemo.batch.cbact03c.Cbact03cJob.XREF_LISTING;
import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.testkit.DatasetFiles;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import com.carddemo.batch.testkit.SampleData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Parity of the Java job with the COBOL program on the sample data and on every edge-case fixture. */
class Cbact03cParityTest {

    private static final String START = "START OF EXECUTION OF PROGRAM CBACT03C";
    private static final String END = "END OF EXECUTION OF PROGRAM CBACT03C";

    @TempDir
    Path tmp;

    @Test
    void sampleData() throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbact03cJob(), tmp.resolve("out"))
                .input(XREFFILE_DD, SampleData.file("cardxref.txt"))
                .run();

        assertCompletedLikeCobol("sample-data", run);
        assertThat(run.result().counters()).containsEntry(COUNTER_XREF_RECORDS_READ, 50L).containsEntry(COUNTER_SYSOUT_LINES, 102L);
        List<String> lines = run.sysout().lines().toList();
        assertThat(lines.get(0)).isEqualTo(START);
        assertThat(lines.get(1)).isEqualTo("050002445376574000000005000000000050" + " ".repeat(14)).isEqualTo(lines.get(2));
        assertThat(lines.get(101)).isEqualTo(END);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unsorted-keys", "unload-format", "raw-content", "empty-file"})
    void edgeCaseCompletes(String scenario) throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbact03cJob(), tmp.resolve("out"))
                .input(XREFFILE_DD, Fixtures.input(scenario, "cardxref.txt"))
                .run();

        assertCompletedLikeCobol(scenario, run);
    }

    @Test
    void unsortedUnloadIsListedInAsciiKeyOrder() throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbact03cJob(), tmp.resolve("out"))
                .input(XREFFILE_DD, Fixtures.input("unsorted-keys", "cardxref.txt"))
                .run();

        List<String> keys = DatasetFiles.readParquet(run.dataset(XREF_LISTING)).stream().map(r -> r.get("xref_card_num")).toList();
        assertThat(keys).isSorted().containsExactly("0500024453765740", "1000000000000005", "4000000000000002",
                "9000000000000003", "A000000000000001", "a000000000000006");
    }

    @Test
    void displayShowsFillerAndUnvalidatedContent() throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbact03cJob(), tmp.resolve("out"))
                .input(XREFFILE_DD, Fixtures.input("raw-content", "cardxref.txt"))
                .run();

        assertThat(run.sysout().lines()).contains("4111111111111111ABCDEFGHI12345-67890FILLERTEXT1234");
        Map<String, String> row = DatasetFiles.readParquet(run.dataset(XREF_LISTING)).get(1);
        assertThat(row).containsExactly(Map.entry("xref_card_num", "4111111111111111"),
                Map.entry("xref_cust_id", "ABCDEFGHI"), Map.entry("xref_acct_id", "12345-67890"));
    }

    @Test
    void emptyKsdsDisplaysOnlyStartAndEnd() throws Exception {
        JobHarness.Run run = JobHarness.forJob(new Cbact03cJob(), tmp.resolve("out"))
                .input(XREFFILE_DD, Fixtures.input("empty-file", "cardxref.txt"))
                .run();

        assertThat(run.sysout().lines()).containsExactly(START, END);
        assertThat(run.result().counters()).containsEntry(COUNTER_XREF_RECORDS_READ, 0L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"open-error-missing-file", "open-error-missing-dd", "duplicate-key", "overlong-record"})
    void openErrorAbends(String scenario) throws Exception {
        Optional<Path> xreffile = switch (scenario) {
            case "open-error-missing-file" -> Optional.of(tmp.resolve("no-such-cardxref.txt"));
            case "open-error-missing-dd" -> Optional.empty();
            default -> Optional.of(Fixtures.input(scenario, "cardxref.txt"));
        };
        Fixtures.AbendRun run = Fixtures.runExpectingAbend(new Cbact03cJob(), tmp.resolve("out"), xreffile);

        assertAbendedLikeCobol(scenario, run);
        assertThat(run.sysout().lines()).contains("ERROR OPENING XREFFILE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"read-error", "close-error"})
    void injectedIoErrorAbends(String scenario) throws Exception {
        Properties fault = Fixtures.properties(Fixtures.input(scenario, "fault.properties"));
        Cbact03cJob job = new Cbact03cJob(path -> new Fixtures.FaultyXrefFile(new KsdsXrefFile(path), fault));
        Fixtures.AbendRun run = Fixtures.runExpectingAbend(job, tmp.resolve("out"), Optional.of(Fixtures.input(scenario, "cardxref.txt")));

        assertAbendedLikeCobol(scenario, run);
        assertThat(run.sysout().lines()).contains(scenario.equals("read-error") ? "ERROR READING XREFFILE" : "ERROR CLOSING XREFFILE");
    }

    private void assertCompletedLikeCobol(String scenario, JobHarness.Run run) throws Exception {
        Properties expected = Fixtures.runProperties(scenario);
        assertThat(expected.getProperty("abended")).isEqualTo("false");
        assertThat(run.result().returnCode()).isEqualTo(Integer.parseInt(expected.getProperty("return_code")));

        ParityAssertions.assertParquetMatches(run.dataset(XREF_LISTING), Fixtures.expected(scenario, "xref_listing.csv"));
        ParityAssertions.assertCsvMatches(run.report(SYSOUT), Fixtures.expected(scenario, "sysout.csv"));
        ParityAssertions.assertTextMatches(Fixtures.writeSysout(tmp, run.sysout()), Fixtures.expected(scenario, "sysout.txt"));

        long listingRows = DatasetFiles.readCsv(Fixtures.expected(scenario, "xref_listing.csv")).size();
        long sysoutRows = DatasetFiles.readCsv(Fixtures.expected(scenario, "sysout.csv")).size();
        assertThat(run.result().counters()).containsExactly(
                Map.entry(COUNTER_XREF_RECORDS_READ, listingRows), Map.entry(COUNTER_SYSOUT_LINES, sysoutRows));
        assertThat(sysoutRows).isEqualTo(2 + 2 * listingRows);
        assertThat(Files.readString(run.manifest())).contains("\"return_code\" : 0", "\"rows\" : " + listingRows);
    }

    private void assertAbendedLikeCobol(String scenario, Fixtures.AbendRun run) throws Exception {
        Properties expected = Fixtures.runProperties(scenario);
        assertThat(expected.getProperty("abended")).isEqualTo("true");
        assertThat(run.abend().abendCode()).isEqualTo(Integer.parseInt(expected.getProperty("abend_code")));
        ParityAssertions.assertTextMatches(Fixtures.writeSysout(tmp, run.sysout()), Fixtures.expected(scenario, "sysout.txt"));
        List<String> lines = run.sysout().lines().toList();
        assertThat(lines.get(0)).isEqualTo(START);
        assertThat(lines.get(lines.size() - 1)).isEqualTo("ABENDING PROGRAM");
        assertThat(lines).doesNotContain(END);
        assertThat(run.manifestWritten()).as("no manifest on abend").isFalse();
    }
}
