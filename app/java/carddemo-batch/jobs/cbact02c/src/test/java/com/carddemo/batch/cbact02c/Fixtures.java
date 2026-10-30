package com.carddemo.batch.cbact02c;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobRunner;
import com.carddemo.batch.testkit.JobHarness;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Locations of the checked-in fixtures (see src/test/resources/fixtures/README.md) and abend-run helpers. */
final class Fixtures {

    static final Path MODULE_DIR = Path.of("").toAbsolutePath();
    static final Path ROOT = MODULE_DIR.resolve("src/test/resources/fixtures");

    private Fixtures() {
    }

    static Path input(String scenario) {
        return ROOT.resolve(scenario).resolve("input/carddata.txt");
    }

    static Path expected(String scenario, String file) {
        return ROOT.resolve(scenario).resolve("expected").resolve(file);
    }

    static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> rtrimmed(String sysout) {
        return sysout.lines().map(DisplayLog::rtrim).toList();
    }

    /** Outcome of a run that ended in CEE3ABD: the abend and the SYSOUT lines written before it. */
    record AbendRun(AbendException abend, List<String> sysout, Path staging) {

        /** Every file left in the output root (an abend must publish nothing). */
        List<Path> filesWritten() {
            try (Stream<Path> s = Files.walk(staging)) {
                return s.filter(Files::isRegularFile).toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Runs the job like {@link JobHarness} (same run date, run id and clock) but keeps the SYSOUT
     * captured before the abend, which {@code JobHarness.run()} cannot return when the job throws.
     */
    static AbendRun runExpectingAbend(BatchJob job, Path staging, Map<String, Path> inputs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JobContext.Builder b = JobContext.builder(job.programId()).runDate(JobHarness.RUN_DATE).runId(JobHarness.RUN_ID)
                .clock(Clock.fixed(JobHarness.CLOCK, ZoneOffset.UTC)).stagingDir(staging)
                .sysout(new PrintStream(out, true, StandardCharsets.UTF_8));
        inputs.forEach(b::input);
        JobContext ctx = b.build();
        AbendException[] abend = new AbendException[1];
        assertThatThrownBy(() -> JobRunner.run(job, ctx)).isInstanceOfSatisfying(AbendException.class, e -> abend[0] = e);
        return new AbendRun(abend[0], rtrimmed(out.toString(StandardCharsets.UTF_8)), staging);
    }
}
