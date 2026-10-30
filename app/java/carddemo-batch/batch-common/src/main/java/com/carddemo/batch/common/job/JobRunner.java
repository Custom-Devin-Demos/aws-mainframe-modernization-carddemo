package com.carddemo.batch.common.job;

import com.carddemo.batch.common.output.OutputLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs a job inside a context and writes the run manifest. Shared by the launcher and tests. */
public final class JobRunner {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JobRunner() {
    }

    /** Runs the job; on success closes writers and writes {@code _manifest.json}. Abends propagate. */
    public static JobResult run(BatchJob job, JobContext ctx) throws Exception {
        JobResult result = job.run(ctx);
        ctx.closeAll();
        writeManifest(ctx, result);
        return result;
    }

    static void writeManifest(JobContext ctx, JobResult result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("program", ctx.programId());
        m.put("run_date", ctx.runDate().toString());
        m.put("run_id", ctx.runId());
        m.put("return_code", result.returnCode());
        m.put("counters", result.counters());
        List<Map<String, Object>> outs = ctx.outputs().stream().map(o -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("dataset", o.dataset());
            e.put("format", o.format().name());
            e.put("data_file", o.dataFile());
            e.put("schema_file", o.schemaFile());
            e.put("rows", o.rows());
            return e;
        }).toList();
        m.put("outputs", outs);
        Path file = ctx.stagingDir().resolve(OutputLayout.manifestFile(ctx.programId(), ctx.runDate(), ctx.runId()));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.writeValueAsString(m) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
