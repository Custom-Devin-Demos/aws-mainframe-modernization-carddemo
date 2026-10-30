package com.carddemo.batch.cbact03c;

import com.carddemo.batch.common.job.AbendException;
import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobRunner;
import com.carddemo.batch.common.output.OutputLayout;
import com.carddemo.batch.testkit.JobHarness;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Properties;

/** Fixture scenarios under src/test/resources/fixtures and helpers shared by the CBACT03C tests. */
final class Fixtures {

    static final Path MODULE_DIR = Paths.get("").toAbsolutePath();
    static final Path ROOT = MODULE_DIR.resolve("src/test/resources/fixtures");

    private Fixtures() {
    }

    static Path input(String scenario, String file) {
        return ROOT.resolve(scenario).resolve("input").resolve(file);
    }

    static Path expected(String scenario, String file) {
        return ROOT.resolve(scenario).resolve("expected").resolve(file);
    }

    static Properties properties(Path file) {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
    }

    /** {@code expected/run.properties}: abended, abend_code, return_code. */
    static Properties runProperties(String scenario) {
        return properties(expected(scenario, "run.properties"));
    }

    static Path writeSysout(Path dir, String sysout) throws IOException {
        Path file = dir.resolve("actual-sysout.txt");
        Files.writeString(file, sysout, StandardCharsets.UTF_8);
        return file;
    }

    /** Outcome of a run that is expected to abend; {@link JobHarness} does not expose SYSOUT when the job throws. */
    record AbendRun(AbendException abend, String sysout, boolean manifestWritten) {
    }

    /** Same fixed run date, run id and clock as {@link JobHarness}, with SYSOUT captured even when the job abends. */
    static AbendRun runExpectingAbend(BatchJob job, Path outputRoot, Optional<Path> xreffile) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JobContext.Builder b = JobContext.builder(job.programId()).runDate(JobHarness.RUN_DATE).runId(JobHarness.RUN_ID)
                .clock(Clock.fixed(JobHarness.CLOCK, ZoneOffset.UTC)).stagingDir(outputRoot)
                .sysout(new PrintStream(out, true, StandardCharsets.UTF_8));
        xreffile.ifPresent(p -> b.input(Cbact03cJob.XREFFILE_DD, p));
        JobContext ctx = b.build();
        try {
            JobRunner.run(job, ctx);
        } catch (AbendException e) {
            boolean manifest = Files.exists(outputRoot.resolve(OutputLayout.manifestFile(ctx.programId(), ctx.runDate(), ctx.runId())));
            return new AbendRun(e, out.toString(StandardCharsets.UTF_8), manifest);
        }
        throw new AssertionError("expected " + job.programId() + " to abend");
    }

    /** Wraps the real KSDS and returns {@code status} on the n-th READ or CLOSE ({@code input/fault.properties}). */
    static final class FaultyXrefFile implements XrefFile {
        private final XrefFile delegate;
        private final String operation;
        private final int occurrence;
        private final String status;
        private int reads;
        private int closes;

        FaultyXrefFile(XrefFile delegate, Properties fault) {
            this.delegate = delegate;
            this.operation = fault.getProperty("operation");
            this.occurrence = Integer.parseInt(fault.getProperty("occurrence"));
            this.status = fault.getProperty("status");
        }

        @Override
        public String open() {
            return delegate.open();
        }

        @Override
        public String read() {
            return operation.equals("READ") && ++reads == occurrence ? status : delegate.read();
        }

        @Override
        public String record() {
            return delegate.record();
        }

        @Override
        public String close() {
            return operation.equals("CLOSE") && ++closes == occurrence ? status : delegate.close();
        }
    }
}
