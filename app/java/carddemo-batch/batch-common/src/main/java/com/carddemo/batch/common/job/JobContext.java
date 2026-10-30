package com.carddemo.batch.common.job;

import com.carddemo.batch.common.output.CsvDatasetWriter;
import com.carddemo.batch.common.output.DatasetWriter;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.output.OutputLayout;
import com.carddemo.batch.common.output.ParquetDatasetWriter;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a job may touch: inputs by JCL DD name (already local), parameters, a clock, a SYSOUT
 * stream, and dataset writers that lay files out per {@link OutputLayout} under a staging directory.
 */
public final class JobContext {

    /** A dataset produced by the job. */
    public record OutputRecord(String dataset, OutputFormat format, String dataFile, String schemaFile, long rows) {
    }

    private final String programId;
    private final LocalDate runDate;
    private final String runId;
    private final Clock clock;
    private final Map<String, Path> inputs;
    private final Map<String, String> params;
    private final Path stagingDir;
    private final PrintStream sysout;
    private final List<OutputRecord> outputs = new ArrayList<>();
    private final Map<String, DatasetWriter> open = new LinkedHashMap<>();

    private JobContext(Builder b) {
        this.programId = b.programId;
        this.runDate = b.runDate;
        this.runId = b.runId;
        this.clock = b.clock;
        this.inputs = Map.copyOf(b.inputs);
        this.params = Map.copyOf(b.params);
        this.stagingDir = b.stagingDir;
        this.sysout = b.sysout;
    }

    public static Builder builder(String programId) {
        return new Builder(programId);
    }

    public String programId() {
        return programId;
    }

    public LocalDate runDate() {
        return runDate;
    }

    public String runId() {
        return runId;
    }

    /** Replaces FUNCTION CURRENT-DATE; fixed in tests via {@code --clock}. */
    public Clock clock() {
        return clock;
    }

    /** Input by JCL DD name, e.g. {@code DALYTRAN}, {@code XREFFILE}. */
    public Path input(String ddName) {
        Path p = inputs.get(ddName);
        if (p == null) {
            throw new IllegalArgumentException(programId + ": missing input DD " + ddName);
        }
        return p;
    }

    public Optional<Path> optionalInput(String ddName) {
        return Optional.ofNullable(inputs.get(ddName));
    }

    public Optional<String> param(String name) {
        return Optional.ofNullable(params.get(name));
    }

    /** DISPLAY output. Goes to stdout (CloudWatch Logs) in production. */
    public PrintStream sysout() {
        return sysout;
    }

    public Path stagingDir() {
        return stagingDir;
    }

    /** Opens a writer for {@code dataset} and writes its {@code _schema.json}. */
    public DatasetWriter openDataset(String dataset, DatasetSchema schema, OutputFormat format) {
        if (open.containsKey(dataset)) {
            throw new IllegalStateException("dataset already opened: " + dataset);
        }
        String dataRel = OutputLayout.dataFile(programId, dataset, runDate, runId, format);
        String schemaRel = OutputLayout.schemaFile(programId, dataset, runDate, runId);
        Path data = stagingDir.resolve(dataRel);
        try {
            Files.createDirectories(data.getParent());
            Files.writeString(stagingDir.resolve(schemaRel), schema.toSparkJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        DatasetWriter inner = format == OutputFormat.PARQUET ? new ParquetDatasetWriter(data, schema) : new CsvDatasetWriter(data, schema);
        DatasetWriter tracked = new DatasetWriter() {
            private boolean closed;

            @Override
            public void write(Map<String, Object> row) {
                inner.write(row);
            }

            @Override
            public long rowCount() {
                return inner.rowCount();
            }

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    inner.close();
                    outputs.add(new OutputRecord(dataset, format, dataRel, schemaRel, inner.rowCount()));
                }
            }
        };
        open.put(dataset, tracked);
        return tracked;
    }

    /** Local path of a dataset's data file inside the staging directory. */
    public Path datasetFile(String dataset, OutputFormat format) {
        return stagingDir.resolve(OutputLayout.dataFile(programId, dataset, runDate, runId, format));
    }

    /** Closes any writer the job left open (on success only). */
    void closeAll() {
        open.values().forEach(DatasetWriter::close);
    }

    public List<OutputRecord> outputs() {
        return Collections.unmodifiableList(outputs);
    }

    public static final class Builder {
        private final String programId;
        private LocalDate runDate = LocalDate.now();
        private String runId = "local";
        private Clock clock = Clock.systemUTC();
        private final Map<String, Path> inputs = new LinkedHashMap<>();
        private final Map<String, String> params = new LinkedHashMap<>();
        private Path stagingDir;
        private PrintStream sysout = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        private Builder(String programId) {
            this.programId = programId;
        }

        public Builder runDate(LocalDate d) {
            this.runDate = d;
            return this;
        }

        public Builder runId(String id) {
            this.runId = id;
            return this;
        }

        public Builder clock(Clock c) {
            this.clock = c;
            return this;
        }

        public Builder input(String ddName, Path path) {
            inputs.put(ddName, path);
            return this;
        }

        public Builder param(String name, String value) {
            params.put(name, value);
            return this;
        }

        public Builder stagingDir(Path dir) {
            this.stagingDir = dir;
            return this;
        }

        public Builder sysout(PrintStream out) {
            this.sysout = out;
            return this;
        }

        public JobContext build() {
            if (stagingDir == null) {
                throw new IllegalStateException("stagingDir is required");
            }
            return new JobContext(this);
        }
    }
}
