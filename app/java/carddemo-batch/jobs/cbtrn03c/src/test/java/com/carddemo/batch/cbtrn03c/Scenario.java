package com.carddemo.batch.cbtrn03c;

import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobResult;
import com.carddemo.batch.common.job.JobRunner;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import com.carddemo.batch.testkit.SampleData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** One fixture scenario: {@code fixtures/<name>/input} (DD files, {@code *.from} sample references, params). */
final class Scenario {

    static final Path MODULE_DIR = Paths.get("").toAbsolutePath();
    static final Path FIXTURES = MODULE_DIR.resolve("src/test/resources/fixtures");
    static final List<String> DD_NAMES = List.of("TRANFILE", "CARDXREF", "TRANTYPE", "TRANCATG", "DATEPARM");

    final String name;
    final Path input;
    final Path expected;

    Scenario(String name) {
        this.name = name;
        this.input = FIXTURES.resolve(name).resolve("input");
        this.expected = FIXTURES.resolve(name).resolve("expected");
    }

    /** DD name to file; a {@code <dd>.from} file names a sample file under app/data/ASCII. */
    Map<String, Path> inputs() throws IOException {
        Map<String, Path> dds = new LinkedHashMap<>();
        for (String dd : DD_NAMES) {
            String base = dd.toLowerCase();
            Path txt = input.resolve(base + ".txt");
            Path from = input.resolve(base + ".from");
            if (Files.exists(txt)) {
                dds.put(dd, txt);
            } else if (Files.exists(from)) {
                dds.put(dd, SampleData.file(Paths.get(Files.readString(from).trim()).getFileName().toString()));
            }
        }
        return dds;
    }

    Properties params() throws IOException {
        Properties p = new Properties();
        Path file = input.resolve("params.properties");
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
            }
        }
        return p;
    }

    Properties expectedResult() throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(expected.resolve("result.properties"))) {
            p.load(r);
        }
        return p;
    }

    boolean abends() throws IOException {
        return Boolean.parseBoolean(expectedResult().getProperty("abended"));
    }

    JobHarness.Run run(Path out) throws Exception {
        JobHarness h = JobHarness.forJob(new Cbtrn03cJob(), out);
        for (Map.Entry<String, Path> e : inputs().entrySet()) {
            h.input(e.getKey(), e.getValue());
        }
        Properties p = params();
        for (String k : p.stringPropertyNames()) {
            h.param(k, p.getProperty(k));
        }
        return h.run();
    }

    /**
     * Same fixed context as {@link JobHarness}, but keeps SYSOUT when the job abends (the harness only
     * returns SYSOUT from a completed run).
     */
    AbendRun runExpectingAbend(Path out) throws Exception {
        ByteArrayOutputStream sysout = new ByteArrayOutputStream();
        JobContext.Builder b = JobContext.builder(Cbtrn03cJob.PROGRAM_ID).runDate(JobHarness.RUN_DATE)
                .runId(JobHarness.RUN_ID).clock(Clock.fixed(JobHarness.CLOCK, ZoneOffset.UTC)).stagingDir(out)
                .sysout(new PrintStream(sysout, true, StandardCharsets.UTF_8));
        for (Map.Entry<String, Path> e : inputs().entrySet()) {
            b.input(e.getKey(), e.getValue());
        }
        Properties p = params();
        for (String k : p.stringPropertyNames()) {
            b.param(k, p.getProperty(k));
        }
        Throwable thrown = null;
        JobResult result = null;
        try {
            result = JobRunner.run(new Cbtrn03cJob(), b.build());
        } catch (Throwable t) {
            thrown = t;
        }
        return new AbendRun(result, thrown, sysout.toString(StandardCharsets.UTF_8));
    }

    record AbendRun(JobResult result, Throwable thrown, String sysout) {
    }

    /** Exact SYSOUT comparison against {@code expected/sysout.txt}. */
    void assertSysout(String sysout, Path tmp) throws IOException {
        Path actual = tmp.resolve(name + "-sysout.txt");
        Files.writeString(actual, sysout, StandardCharsets.UTF_8);
        ParityAssertions.assertTextMatches(actual, expected.resolve("sysout.txt"));
    }

    /** Rebuilds the 133-byte print image from the {@code line_text} column and compares it exactly. */
    void assertPrintImage(Path reportCsv, Path tmp) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Map<String, String> row : com.carddemo.batch.testkit.DatasetFiles.readCsv(reportCsv)) {
            lines.add(ReportLines.toRecord(row.get("line_text")));
        }
        Path actual = tmp.resolve(name + "-tranrept.txt");
        Files.write(actual, lines, StandardCharsets.US_ASCII);
        ParityAssertions.assertTextMatches(actual, expected.resolve("transaction_report.txt"));
    }

    List<Map<String, String>> expectedRows() {
        return com.carddemo.batch.testkit.DatasetFiles.readCsv(expected.resolve("transaction_report.csv"));
    }

    long expectedCount(String lineType) {
        return expectedRows().stream().filter(r -> r.get("line_type").equals(lineType)).count();
    }
}
