package com.carddemo.batch.cbact02c;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** OPEN failures: 0000-CARDFILE-OPEN → 9910-DISPLAY-IO-STATUS → 9999-ABEND-PROGRAM (CEE3ABD U999). */
class AbendParityTest {

    @TempDir
    Path out;

    private static void assertAbended(Fixtures.AbendRun run, List<String> expectedSysout, String status) {
        assertThat(run.sysout()).isEqualTo(expectedSysout);
        assertThat(run.abend().abendCode()).isEqualTo(Cbact02cJob.ABEND_CODE);
        assertThat(run.abend().getMessage()).contains("OPEN CARDFILE failed with file status " + status);
        assertThat(run.filesWritten()).as("an abend publishes no datasets, schemas or manifest").isEmpty();
    }

    /** Expected SYSOUT from the GnuCOBOL run with no CARDFILE (OPEN status 35). */
    @Test
    void missingFileAbendsWithStatus35() {
        Fixtures.AbendRun run = Fixtures.runExpectingAbend(new Cbact02cJob(), out,
                Map.of(Cbact02cJob.CARDFILE_DD, out.resolveSibling(out.getFileName() + "-absent").resolve("carddata.txt")));
        assertAbended(run, Fixtures.lines(Fixtures.expected("missing-file", "sysout.txt")), "35");
        assertThat(run.abend().getMessage()).contains("no such file");
    }

    @Test
    void missingDdAbendsWithStatus35() {
        Fixtures.AbendRun run = Fixtures.runExpectingAbend(new Cbact02cJob(), out, Map.of());
        assertAbended(run, Fixtures.lines(Fixtures.expected("missing-file", "sysout.txt")), "35");
        assertThat(run.abend().getMessage()).contains("DD CARDFILE not allocated");
    }

    /** Unloads that cannot be a CARDFILE KSDS image: OPEN status 39 (expected SYSOUT hand-derived). */
    @ParameterizedTest
    @ValueSource(strings = {"malformed-overlength", "malformed-duplicate-key", "malformed-non-ascii"})
    void malformedUnloadAbendsWithStatus39(String scenario) {
        Fixtures.AbendRun run = Fixtures.runExpectingAbend(new Cbact02cJob(), out, Map.of(Cbact02cJob.CARDFILE_DD, Fixtures.input(scenario)));
        assertAbended(run, Fixtures.lines(Fixtures.expected(scenario, "sysout.txt")), "39");
    }

    @Test
    void unreadableFileAbendsWithStatus30() throws Exception {
        Path file = Files.writeString(Files.createTempFile("carddata", ".txt"), "");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        try {
            assumeFalse(Files.isReadable(file), "running as a user that bypasses file permissions");
            Fixtures.AbendRun run = Fixtures.runExpectingAbend(new Cbact02cJob(), out, Map.of(Cbact02cJob.CARDFILE_DD, file));
            assertAbended(run, List.of(Cbact02cJob.START_MESSAGE, "ERROR OPENING CARDFILE", "FILE STATUS IS: NNNN0030",
                    "ABENDING PROGRAM"), "30");
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            Files.delete(file);
        }
    }
}
