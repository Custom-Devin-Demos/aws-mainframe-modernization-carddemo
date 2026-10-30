package com.carddemo.batch.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.common.output.CsvDatasetWriter;
import com.carddemo.batch.common.output.DatasetWriter;
import com.carddemo.batch.common.output.ParquetDatasetWriter;
import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.record.FixedWidthFile;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParityAssertionsTest {

    @TempDir
    Path tmp;

    static final DatasetSchema XREF = DatasetSchema.fromLayout("xref", "xref", Copybooks.CARD_XREF_RECORD);

    @Test
    void parquetOutputMatchesCsvRenderingOfSameRows() {
        Path parquet = tmp.resolve("x.parquet");
        Path csv = tmp.resolve("x.csv");
        var recs = FixedWidthFile.read(SampleData.file("cardxref.txt"), Copybooks.CARD_XREF_RECORD);
        try (DatasetWriter p = new ParquetDatasetWriter(parquet, XREF); DatasetWriter c = new CsvDatasetWriter(csv, XREF)) {
            recs.forEach(r -> {
                p.write(r);
                c.write(r);
            });
        }
        ParityAssertions.assertParquetMatches(parquet, csv);
        assertThat(DatasetFiles.readParquet(parquet)).hasSize(50).first()
                .satisfies(row -> assertThat(row).containsEntry("xref_card_num", "0500024453765740").containsEntry("xref_acct_id", "00000000050"));
    }

    @Test
    void reportsFirstDifferingCell() {
        Path parquet = tmp.resolve("x.parquet");
        Path csv = tmp.resolve("x.csv");
        var rec = FixedWidthFile.read(SampleData.file("cardxref.txt"), Copybooks.CARD_XREF_RECORD).get(0);
        try (DatasetWriter p = new ParquetDatasetWriter(parquet, XREF); DatasetWriter c = new CsvDatasetWriter(csv, XREF)) {
            p.write(rec);
            c.write(rec.copy().set("XREF-CUST-ID", "7"));
        }
        assertThatThrownBy(() -> ParityAssertions.assertParquetMatches(parquet, csv))
                .isInstanceOf(AssertionError.class).hasMessageContaining("column xref_cust_id");
    }

    @Test
    void csvParserHandlesQuotes() {
        assertThat(DatasetFiles.parseCsv("a,b\n\"x, y\",\"he said \"\"hi\"\"\"\n"))
                .containsExactly(java.util.List.of("a", "b"), java.util.List.of("x, y", "he said \"hi\""));
    }
}
