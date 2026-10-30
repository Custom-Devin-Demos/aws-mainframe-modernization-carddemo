package com.carddemo.batch.testkit;

import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobResult;
import com.carddemo.batch.common.job.JobRunner;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.output.OutputLayout;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Runs a job in-process against local inputs with a fixed run date, run id and clock, capturing SYSOUT.
 *
 * <pre>
 * JobHarness.Run run = JobHarness.forJob(new Cbtrn02cJob(), tempDir)
 *     .input("DALYTRAN", SampleData.file("dailytran.txt"))
 *     .run();
 * ParityAssertions.assertParquetMatches(run.dataset("posted_transactions"), expected.resolve("posted_transactions.csv"));
 * </pre>
 */
public final class JobHarness {

    public static final LocalDate RUN_DATE = LocalDate.of(2022, 7, 6);
    public static final String RUN_ID = "parity";
    public static final Instant CLOCK = Instant.parse("2022-07-06T00:00:00Z");

    private final BatchJob job;
    private final JobContext.Builder builder;
    private final Path outputRoot;
    private final ByteArrayOutputStream sysout = new ByteArrayOutputStream();

    private JobHarness(BatchJob job, Path outputRoot) {
        this.job = job;
        this.outputRoot = outputRoot;
        this.builder = JobContext.builder(job.programId()).runDate(RUN_DATE).runId(RUN_ID)
                .clock(Clock.fixed(CLOCK, ZoneOffset.UTC)).stagingDir(outputRoot)
                .sysout(new PrintStream(sysout, true, StandardCharsets.UTF_8));
    }

    public static JobHarness forJob(BatchJob job, Path outputRoot) {
        return new JobHarness(job, outputRoot);
    }

    public JobHarness input(String ddName, Path file) {
        builder.input(ddName, file);
        return this;
    }

    public JobHarness param(String name, String value) {
        builder.param(name, value);
        return this;
    }

    public JobHarness clock(Instant instant) {
        builder.clock(Clock.fixed(instant, ZoneOffset.UTC));
        return this;
    }

    public Run run() throws Exception {
        JobContext ctx = builder.build();
        JobResult result = JobRunner.run(job, ctx);
        return new Run(result, ctx, outputRoot, sysout.toString(StandardCharsets.UTF_8));
    }

    /** Outcome of a harness run. */
    public record Run(JobResult result, JobContext context, Path outputRoot, String sysout) {

        public Path dataset(String dataset) {
            return context.datasetFile(dataset, OutputFormat.PARQUET);
        }

        public Path report(String dataset) {
            return context.datasetFile(dataset, OutputFormat.CSV);
        }

        public Path schema(String dataset) {
            return outputRoot.resolve(OutputLayout.schemaFile(context.programId(), dataset, context.runDate(), context.runId()));
        }

        public Path manifest() {
            return outputRoot.resolve(OutputLayout.manifestFile(context.programId(), context.runDate(), context.runId()));
        }
    }
}
