package com.carddemo.batch.cbtrn03c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Hand-built scenarios for the rules and branches the sample data does not reach (fixtures/README.md). */
class Cbtrn03cEdgeCaseParityTest {

    @TempDir
    Path out;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"presort-filter-order", "pagination", "eof-last-account", "edited-amounts-overflow",
            "date-filter-next-sentence", "no-selected-records", "empty-dateparm", "dateparm-from-params"})
    void completedRunMatchesCobol(String name) throws Exception {
        Scenario s = new Scenario(name);
        assertThat(s.abends()).isFalse();
        JobHarness.Run run = s.run(out);
        assertThat(run.result().returnCode()).isEqualTo(Integer.parseInt(s.expectedResult().getProperty("return_code")));
        s.assertSysout(run.sysout(), out);
        Path report = run.report(Cbtrn03cJob.DATASET);
        if (Files.exists(s.expected.resolve("transaction_report.csv"))) {
            ParityAssertions.assertCsvMatches(report, s.expected.resolve("transaction_report.csv"));
            s.assertPrintImage(report, out);
        } else {
            assertThat(com.carddemo.batch.testkit.DatasetFiles.readCsv(report)).isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"invalid-xref", "invalid-trantype", "invalid-trancatg", "missing-dateparm-abend"})
    void abendMatchesCobol(String name) throws Exception {
        Scenario s = new Scenario(name);
        assertThat(s.abends()).isTrue();
        Scenario.AbendRun run = s.runExpectingAbend(out);
        assertThat(run.thrown()).isInstanceOf(AbendException.class);
        assertThat(((AbendException) run.thrown()).abendCode())
                .isEqualTo(Integer.parseInt(s.expectedResult().getProperty("return_code")));
        s.assertSysout(run.sysout(), out);
        assertThat(run.sysout()).endsWith("ABENDING PROGRAM\n");
        try (var files = Files.walk(out)) {
            assertThat(files.filter(p -> p.getFileName().toString().equals("_manifest.json"))).isEmpty();
        }
    }

    @Test
    void invalidLookupsDisplayKeyAndStatus23() throws Exception {
        assertThat(new Scenario("invalid-xref").runExpectingAbend(out).sysout())
                .contains("INVALID CARD NUMBER : 9999999999999999\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n");
        assertThat(new Scenario("invalid-trantype").runExpectingAbend(out.resolve("t")).sysout())
                .contains("INVALID TRANSACTION TYPE : 09\nFILE STATUS IS: NNNN0023\n");
        assertThat(new Scenario("invalid-trancatg").runExpectingAbend(out.resolve("c")).sysout())
                .contains("INVALID TRAN CATG KEY : 010009\nFILE STATUS IS: NNNN0023\n");
    }

    @Test
    void missingDateparmIsOpenErrorStatus35() throws Exception {
        Scenario.AbendRun run = new Scenario("missing-dateparm-abend").runExpectingAbend(out);
        assertThat(run.sysout()).isEqualTo("START OF EXECUTION OF PROGRAM CBTRN03C\nERROR OPENING DATE PARM FILE\n"
                + "FILE STATUS IS: NNNN0035\nABENDING PROGRAM\n");
    }

    @Test
    void nextSentenceEndsTheLoopWithoutTotals() throws Exception {
        Scenario s = new Scenario("date-filter-next-sentence");
        JobHarness.Run run = s.run(out);
        assertThat(lineTypes(run)).doesNotContain("PAGE_TOTAL", "ACCOUNT_TOTAL", "GRAND_TOTAL");
        assertThat(run.result().counters()).containsEntry("sort_included", 5L).containsEntry("tranfile_read", 3L)
                .containsEntry("detail_lines", 2L);
        assertThat(run.sysout()).doesNotContain("TRAN-AMT");
    }

    @Test
    void eofAddsLastAmountAgainAndSkipsLastAccountTotal() throws Exception {
        JobHarness.Run run = new Scenario("eof-last-account").run(out);
        List<Map<String, String>> rows = com.carddemo.batch.testkit.DatasetFiles.readCsv(run.report(Cbtrn03cJob.DATASET));
        Map<String, String> pageTotal = rows.stream().filter(r -> r.get("line_type").equals("PAGE_TOTAL")).findFirst().orElseThrow();
        // 100 + 200 + 30 + 40 + 5.25 = 375.25, plus the last 5.25 again.
        assertThat(pageTotal.get("amount")).isEqualTo("380.50");
        assertThat(rows.stream().filter(r -> r.get("line_type").equals("ACCOUNT_TOTAL")).count()).isEqualTo(1);
        assertThat(run.sysout()).contains("TRAN-AMT 0000000052E\nWS-PAGE-TOTAL0000003752E\n");
    }

    @Test
    void pageBreaksWhenLineCounterIsAMultipleOf20() throws Exception {
        JobHarness.Run run = new Scenario("pagination").run(out);
        List<Map<String, String>> rows = com.carddemo.batch.testkit.DatasetFiles.readCsv(run.report(Cbtrn03cJob.DATASET));
        List<String> headerLines = rows.stream().filter(r -> r.get("line_type").equals("HEADER")).map(r -> r.get("line_no")).toList();
        assertThat(headerLines).containsExactly("1", "23", "63");
        assertThat(run.result().counters()).containsEntry("pages", 3L).containsEntry("page_totals", 3L)
                .containsEntry("account_totals", 1L).containsEntry("detail_lines", 49L);
    }

    @Test
    void emptyTranfileWritesNoReportLines() throws Exception {
        JobHarness.Run run = new Scenario("no-selected-records").run(out);
        assertThat(run.result().counters()).containsEntry("sort_included", 0L).containsEntry("report_lines", 0L);
        assertThat(Files.readString(run.manifest())).contains("\"rows\" : 0");
    }

    private static String lineTypes(JobHarness.Run run) {
        return com.carddemo.batch.testkit.DatasetFiles.readCsv(run.report(Cbtrn03cJob.DATASET)).stream()
                .map(r -> r.get("line_type")).collect(Collectors.joining(","));
    }
}
