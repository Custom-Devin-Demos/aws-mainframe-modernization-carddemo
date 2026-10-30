package com.carddemo.batch.cbact02c;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * READ and CLOSE statuses an in-memory KSDS never returns, injected through a scripted CARDFILE.
 * Expected SYSOUT follows 1000-CARDFILE-GET-NEXT / 9000-CARDFILE-CLOSE / 9910 by hand (fixtures/README.md).
 */
class FileStatusBranchesTest {

    private static final String CARD_1 = "0500024453765740" + "00000000050747" + "Aniya Von";
    private static final String CARD_2 = "0683586198171516" + "00000000027567" + "Ward Jones";

    @TempDir
    Path out;

    /** Returns the given OPEN/CLOSE statuses and READ results in sequence. */
    private static final class ScriptedCardFile implements CardFile {
        private final String openStatus;
        private final List<ReadResult> reads;
        private final String closeStatus;
        private int next;

        ScriptedCardFile(String openStatus, List<ReadResult> reads, String closeStatus) {
            this.openStatus = openStatus;
            this.reads = reads;
            this.closeStatus = closeStatus;
        }

        @Override
        public String open() {
            return openStatus;
        }

        @Override
        public ReadResult readNext() {
            return next < reads.size() ? reads.get(next++) : ReadResult.status(END_OF_FILE);
        }

        @Override
        public String close() {
            return closeStatus;
        }
    }

    private Fixtures.AbendRun abend(String open, List<CardFile.ReadResult> reads, String close) {
        return Fixtures.runExpectingAbend(new Cbact02cJob(ctx -> new ScriptedCardFile(open, reads, close)), out, Map.of());
    }

    private static String image(String s) {
        return (s + " ".repeat(150)).substring(0, 150);
    }

    @ParameterizedTest(name = "READ status {0} -> {1}")
    @CsvSource({
        "30, FILE STATUS IS: NNNN0030",   // permanent I/O error
        "04, FILE STATUS IS: NNNN0004",   // only '00' counts as a successful READ
        "23, FILE STATUS IS: NNNN0023",
        "93, FILE STATUS IS: NNNN9243",   // 9x: STAT2 shown as its EBCDIC code point
    })
    void readErrorAfterTwoRecordsAbends(String status, String statusLine) {
        List<CardFile.ReadResult> reads = new ArrayList<>(List.of(
                CardFile.ReadResult.ok(image(CARD_1)), CardFile.ReadResult.ok(image(CARD_2)), CardFile.ReadResult.status(status)));
        Fixtures.AbendRun run = abend(CardFile.OK, reads, CardFile.OK);
        assertThat(run.sysout()).containsExactly(Cbact02cJob.START_MESSAGE, CARD_1, CARD_2,
                "ERROR READING CARDFILE", statusLine, "ABENDING PROGRAM");
        assertThat(run.abend().abendCode()).isEqualTo(999);
        assertThat(run.abend().getMessage()).contains("READ CARDFILE failed with file status " + status);
        assertThat(run.filesWritten()).isEmpty();
    }

    @Test
    void openStatus97IsTreatedAsAnError() {
        Fixtures.AbendRun run = abend("97", List.of(), CardFile.OK);
        assertThat(run.sysout()).containsExactly(Cbact02cJob.START_MESSAGE, "ERROR OPENING CARDFILE",
                "FILE STATUS IS: NNNN9247", "ABENDING PROGRAM");
    }

    @Test
    void closeErrorAbendsAfterAllRecordsAndBeforeEndMessage() {
        Fixtures.AbendRun run = abend(CardFile.OK, List.of(CardFile.ReadResult.ok(image(CARD_1))), "30");
        assertThat(run.sysout()).containsExactly(Cbact02cJob.START_MESSAGE, CARD_1,
                "ERROR CLOSING CARDFILE", "FILE STATUS IS: NNNN0030", "ABENDING PROGRAM");
        assertThat(run.abend().getMessage()).contains("CLOSE CARDFILE failed with file status 30");
        assertThat(run.filesWritten()).isEmpty();
    }

    @Test
    void ksdsReadAndCloseBeforeOpenReturnNotOpenStatuses() {
        KsdsCardFile f = new KsdsCardFile(java.util.Optional.empty());
        assertThat(f.readNext().status()).isEqualTo("47");
        assertThat(f.close()).isEqualTo("42");
    }
}
