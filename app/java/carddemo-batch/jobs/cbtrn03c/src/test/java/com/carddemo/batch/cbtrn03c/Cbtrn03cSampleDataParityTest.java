package com.carddemo.batch.cbtrn03c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import com.carddemo.batch.testkit.SampleData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BR-CBTRN03C-01..14 on app/data/ASCII (TRANFILE derived from dailytran.txt, design section 7.2). */
class Cbtrn03cSampleDataParityTest {

    static final Scenario SAMPLE = new Scenario("sample-data");
    @TempDir
    static Path out;
    static JobHarness.Run run;

    @BeforeAll
    static void runJob() throws Exception {
        run = JobHarness.forJob(new Cbtrn03cJob(), out)
                .input("TRANFILE", SAMPLE.input.resolve("tranfile.txt"))
                .input("CARDXREF", SampleData.file("cardxref.txt"))
                .input("TRANTYPE", SampleData.file("trantype.txt"))
                .input("TRANCATG", SampleData.file("trancatg.txt"))
                .input("DATEPARM", SAMPLE.input.resolve("dateparm.txt"))
                .run();
    }

    @Test
    void tranfileFixtureIsDailytranWithProcTsFromOrigTs() throws Exception {
        List<String> daily = Files.readAllLines(SampleData.file("dailytran.txt"), StandardCharsets.US_ASCII).stream()
                .map(l -> l.replace("\r", "")).filter(l -> !l.isBlank()).toList();
        List<String> tranfile = Files.readAllLines(SAMPLE.input.resolve("tranfile.txt"), StandardCharsets.US_ASCII);
        assertThat(tranfile).hasSize(daily.size());
        for (int i = 0; i < daily.size(); i++) {
            String d = ReportLines.pad(daily.get(i), 350);
            assertThat(tranfile.get(i)).isEqualTo(d.substring(0, 304) + d.substring(278, 304) + d.substring(330));
        }
    }

    @Test
    void transactionReportMatchesCobol() {
        ParityAssertions.assertCsvMatches(run.report(Cbtrn03cJob.DATASET), SAMPLE.expected.resolve("transaction_report.csv"));
    }

    @Test
    void printImageMatchesCobol(@TempDir Path tmp) throws Exception {
        SAMPLE.assertPrintImage(run.report(Cbtrn03cJob.DATASET), tmp);
    }

    @Test
    void sysoutMatchesCobol(@TempDir Path tmp) throws Exception {
        SAMPLE.assertSysout(run.sysout(), tmp);
        assertThat(run.sysout()).startsWith("START OF EXECUTION OF PROGRAM CBTRN03C\nReporting from 2022-01-01 to 2022-07-06\n")
                .contains("TRAN-AMT 0000007656F\nWS-PAGE-TOTAL0000045841I\n")
                .endsWith("END OF EXECUTION OF PROGRAM CBTRN03C\n");
    }

    @Test
    void returnCodeAndCounters() {
        assertThat(run.result().returnCode()).isZero();
        assertThat(run.result().counters())
                .containsEntry("transact_unloaded", 300L)
                .containsEntry("sort_included", 300L)
                .containsEntry("sort_omitted", 0L)
                .containsEntry("tranfile_read", 300L)
                .containsEntry("detail_lines", SAMPLE.expectedCount("DETAIL"))
                .containsEntry("account_totals", SAMPLE.expectedCount("ACCOUNT_TOTAL"))
                .containsEntry("page_totals", SAMPLE.expectedCount("PAGE_TOTAL"))
                .containsEntry("pages", SAMPLE.expectedCount("HEADER"))
                .containsEntry("report_lines", (long) SAMPLE.expectedRows().size());
    }

    @Test
    void publishesSchemaAndManifest() throws Exception {
        assertThat(Files.readString(run.schema(Cbtrn03cJob.DATASET))).isEqualTo(Cbtrn03cJob.SCHEMA.toSparkJson());
        assertThat(Files.readString(run.manifest())).contains("\"return_code\" : 0").contains("\"rows\" : 501");
    }
}
