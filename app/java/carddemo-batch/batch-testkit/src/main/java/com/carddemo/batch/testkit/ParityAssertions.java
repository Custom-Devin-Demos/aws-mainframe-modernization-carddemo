package com.carddemo.batch.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Row-by-row, column-by-column comparison of job outputs against expected fixtures. */
public final class ParityAssertions {

    private ParityAssertions() {
    }

    /** Job Parquet output vs expected CSV fixture (same column names, canonical values, same order). */
    public static void assertParquetMatches(Path actualParquet, Path expectedCsv) {
        assertRowsMatch(DatasetFiles.readParquet(actualParquet), DatasetFiles.readCsv(expectedCsv), actualParquet + " vs " + expectedCsv);
    }

    /** Job CSV output vs expected CSV fixture. */
    public static void assertCsvMatches(Path actualCsv, Path expectedCsv) {
        assertRowsMatch(DatasetFiles.readCsv(actualCsv), DatasetFiles.readCsv(expectedCsv), actualCsv + " vs " + expectedCsv);
    }

    /** Exact text comparison (e.g. a 133-column print image or a legacy fixed-width file). */
    public static void assertTextMatches(Path actual, Path expected) {
        try {
            List<String> a = Files.readAllLines(actual, StandardCharsets.UTF_8);
            List<String> e = Files.readAllLines(expected, StandardCharsets.UTF_8);
            for (int i = 0; i < Math.min(a.size(), e.size()); i++) {
                if (!a.get(i).equals(e.get(i))) {
                    fail("line %d differs (%s vs %s)%n  actual:   [%s]%n  expected: [%s]", i + 1, actual, expected, a.get(i), e.get(i));
                }
            }
            assertThat(a.size()).as("line count of %s vs %s", actual, expected).isEqualTo(e.size());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static void assertRowsMatch(List<Map<String, String>> actual, List<Map<String, String>> expected, String what) {
        int n = Math.min(actual.size(), expected.size());
        for (int i = 0; i < n; i++) {
            Map<String, String> a = actual.get(i);
            Map<String, String> e = expected.get(i);
            assertThat(a.keySet()).as("%s: columns of row %d", what, i + 1).containsExactlyElementsOf(e.keySet());
            for (String col : e.keySet()) {
                if (!e.get(col).equals(a.get(col))) {
                    fail("%s: row %d column %s: actual [%s] expected [%s]%n  actual row:   %s%n  expected row: %s",
                            what, i + 1, col, a.get(col), e.get(col), a, e);
                }
            }
        }
        assertThat(actual.size()).as("%s: row count", what).isEqualTo(expected.size());
    }
}
