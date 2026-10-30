package com.carddemo.batch.common.job;

/**
 * One migrated COBOL batch program. Implementations are discovered with {@link java.util.ServiceLoader}
 * ({@code META-INF/services/com.carddemo.batch.common.job.BatchJob}) by {@link BatchLauncher}.
 */
public interface BatchJob {

    /** The COBOL PROGRAM-ID this job replaces, e.g. {@code CBTRN02C}. */
    String programId();

    JobResult run(JobContext context) throws Exception;
}
