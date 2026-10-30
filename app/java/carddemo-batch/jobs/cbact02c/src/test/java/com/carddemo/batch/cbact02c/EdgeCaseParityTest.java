package com.carddemo.batch.cbact02c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Branches the sample data does not exercise; expected outputs come from GnuCOBOL runs (fixtures/README.md). */
class EdgeCaseParityTest {

    @TempDir
    Path out;

    private JobHarness.Run run(String scenario) throws Exception {
        return JobHarness.forJob(new Cbact02cJob(), out).input(Cbact02cJob.CARDFILE_DD, Fixtures.input(scenario)).run();
    }

    @ParameterizedTest(name = "{0}: {1} cards")
    @CsvSource({
        "key-order,     7",   // BR-CBACT02C-03: unload out of key order, space-padded short key
        "unload-format, 4",   // BR-CBACT02C-08: CRLF, stripped trailing blanks, blank lines, no final newline
        "raw-content,   4",   // BR-CBACT02C-04/05: non-numeric PIC 9 bytes, FILLER content, CSV quoting
        "empty-file,    0",   // BR-CBACT02C-06: first READ returns status 10
    })
    void outputsMatchCobol(String scenario, long cards) throws Exception {
        JobHarness.Run run = run(scenario);
        ParityAssertions.assertParquetMatches(run.dataset(Cbact02cJob.CARD_LISTING), Fixtures.expected(scenario, "card_listing.csv"));
        ParityAssertions.assertCsvMatches(run.report(Cbact02cJob.SYSOUT), Fixtures.expected(scenario, "sysout.csv"));
        assertThat(run.result().returnCode()).isZero();
        assertThat(run.result().counters()).containsEntry(Cbact02cJob.CARD_RECORDS_READ, cards)
                .containsEntry(Cbact02cJob.SYSOUT_LINES, cards + 2);
    }

    @Test
    void keyOrderInputIsNotSortedButOutputIs() throws Exception {
        List<String> inputKeys = Fixtures.lines(Fixtures.input("key-order")).stream().map(l -> l.substring(0, 16)).toList();
        assertThat(inputKeys).isNotEqualTo(inputKeys.stream().sorted().toList());
        List<String> displayed = run("key-order").sysout().lines().skip(1).limit(inputKeys.size()).map(l -> l.substring(0, 16)).toList();
        assertThat(displayed).isEqualTo(inputKeys.stream().sorted().toList());
    }

    @Test
    void displayShowsFillerBytesThatTheDatasetDrops() throws Exception {
        List<String> lines = run("raw-content").sysout().lines().toList();
        assertThat(lines.get(2)).hasSize(150).endsWith("NON-BLANK FILLER CONTENT" + " ".repeat(35));
        assertThat(lines.get(4)).hasSize(150).endsWith("*");
    }

    @Test
    void emptyFileDisplaysOnlyStartAndEnd() throws Exception {
        assertThat(run("empty-file").sysout().lines())
                .containsExactly(Cbact02cJob.START_MESSAGE, Cbact02cJob.END_MESSAGE);
    }
}
