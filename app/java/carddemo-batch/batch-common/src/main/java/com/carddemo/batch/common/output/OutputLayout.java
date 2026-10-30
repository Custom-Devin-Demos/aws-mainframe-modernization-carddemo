package com.carddemo.batch.common.output;

import java.time.LocalDate;
import java.util.Locale;

/**
 * S3 / Databricks naming convention, relative to the output root (e.g. {@code s3://bucket/carddemo}):
 *
 * <pre>
 * curated/{program}/{dataset}/run_date={yyyy-MM-dd}/run_id={runId}/part-00000.{parquet|csv}
 * curated/{program}/{dataset}/run_date={yyyy-MM-dd}/run_id={runId}/_schema.json
 * curated/{program}/_runs/run_date={yyyy-MM-dd}/run_id={runId}/_manifest.json
 * </pre>
 *
 * Underscore-prefixed files are ignored by Spark readers, and {@code run_date}/{@code run_id} are
 * discovered as partition columns.
 */
public final class OutputLayout {

    public static final String ZONE = "curated";
    public static final String DATA_FILE = "part-00000";
    public static final String SCHEMA_FILE = "_schema.json";
    public static final String MANIFEST_FILE = "_manifest.json";

    private OutputLayout() {
    }

    public static String datasetDir(String program, String dataset, LocalDate runDate, String runId) {
        return ZONE + "/" + program.toLowerCase(Locale.ROOT) + "/" + dataset + "/" + partition(runDate, runId);
    }

    public static String dataFile(String program, String dataset, LocalDate runDate, String runId, OutputFormat format) {
        return datasetDir(program, dataset, runDate, runId) + "/" + DATA_FILE + "." + format.extension();
    }

    public static String schemaFile(String program, String dataset, LocalDate runDate, String runId) {
        return datasetDir(program, dataset, runDate, runId) + "/" + SCHEMA_FILE;
    }

    public static String manifestFile(String program, LocalDate runDate, String runId) {
        return ZONE + "/" + program.toLowerCase(Locale.ROOT) + "/_runs/" + partition(runDate, runId) + "/" + MANIFEST_FILE;
    }

    private static String partition(LocalDate runDate, String runId) {
        return "run_date=" + runDate + "/run_id=" + runId;
    }
}
