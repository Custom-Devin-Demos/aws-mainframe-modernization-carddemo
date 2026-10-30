package com.carddemo.batch.common.job;

import com.carddemo.batch.common.io.Storage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

/**
 * Container entry point.
 *
 * <pre>
 * java -jar cbtrn02c-job.jar \
 *   [--job CBTRN02C] \
 *   --input DALYTRAN=s3://bucket/carddemo/raw/dailytran.txt --input XREFFILE=... \
 *   --output-root s3://bucket/carddemo \
 *   --run-date 2022-07-06 --run-id $AWS_BATCH_JOB_ID \
 *   [--param START-DATE=2022-01-01] [--clock 2022-07-06T00:00:00Z]
 * </pre>
 *
 * Exit status: 0 for COBOL RETURN-CODE 0 or 4 (RC is in the manifest and logged as
 * {@code CARDDEMO_RETURN_CODE=n}); the RC itself when it is 8 or higher; 99 on abend.
 */
public final class BatchLauncher {

    public static final int ABEND_EXIT = 99;

    private BatchLauncher() {
    }

    public static void main(String[] args) {
        System.exit(launch(args));
    }

    public static int launch(String[] args) {
        String jobName = null;
        String outputRoot = null;
        LocalDate runDate = LocalDate.now(ZoneOffset.UTC);
        String runId = "local";
        Clock clock = Clock.systemUTC();
        List<String[]> inputs = new ArrayList<>();
        List<String[]> params = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String v = i + 1 < args.length ? args[i + 1] : null;
            switch (a) {
                case "--job" -> { jobName = v; i++; }
                case "--output-root" -> { outputRoot = v; i++; }
                case "--run-date" -> { runDate = LocalDate.parse(v); i++; }
                case "--run-id" -> { runId = v; i++; }
                case "--clock" -> { clock = Clock.fixed(Instant.parse(v), ZoneOffset.UTC); i++; }
                case "--input" -> { inputs.add(split(v)); i++; }
                case "--param" -> { params.add(split(v)); i++; }
                default -> throw new IllegalArgumentException("unknown argument " + a);
            }
        }
        if (outputRoot == null) {
            throw new IllegalArgumentException("--output-root is required");
        }
        BatchJob job = findJob(jobName);
        Storage storage = new Storage();
        try {
            Path work = Files.createTempDirectory("carddemo-" + job.programId().toLowerCase(Locale.ROOT));
            Path staging = Storage.isS3(outputRoot) ? work.resolve("out") : Path.of(outputRoot);
            JobContext.Builder b = JobContext.builder(job.programId()).runDate(runDate).runId(runId).clock(clock).stagingDir(staging);
            for (String[] in : inputs) {
                b.input(in[0], storage.fetch(in[1], work.resolve("in")));
            }
            for (String[] p : params) {
                b.param(p[0], p[1]);
            }
            JobContext ctx = b.build();
            JobResult result = JobRunner.run(job, ctx);
            storage.publish(staging, outputRoot);
            System.out.println("CARDDEMO_RETURN_CODE=" + result.returnCode());
            return result.returnCode() <= 4 ? 0 : result.returnCode();
        } catch (AbendException e) {
            System.out.println("ABENDING PROGRAM " + job.programId() + " U" + e.abendCode() + ": " + e.getMessage());
            return ABEND_EXIT;
        } catch (Exception e) {
            e.printStackTrace();
            return ABEND_EXIT;
        }
    }

    static BatchJob findJob(String name) {
        List<BatchJob> jobs = new ArrayList<>();
        ServiceLoader.load(BatchJob.class).forEach(jobs::add);
        if (name == null) {
            if (jobs.size() != 1) {
                throw new IllegalArgumentException("--job required; available: " + jobs.stream().map(BatchJob::programId).toList());
            }
            return jobs.get(0);
        }
        return jobs.stream().filter(j -> j.programId().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown job " + name));
    }

    private static String[] split(String kv) {
        int eq = kv.indexOf('=');
        if (eq <= 0) {
            throw new IllegalArgumentException("expected NAME=VALUE: " + kv);
        }
        return new String[] {kv.substring(0, eq), kv.substring(eq + 1)};
    }
}
