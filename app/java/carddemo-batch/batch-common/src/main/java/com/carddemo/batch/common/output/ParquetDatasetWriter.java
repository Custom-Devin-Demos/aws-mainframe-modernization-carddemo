package com.carddemo.batch.common.output;

import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Map;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;

/** Single-file Parquet writer (SNAPPY). Rows are written in call order, so output order is deterministic. */
public final class ParquetDatasetWriter implements DatasetWriter {

    public static GenericData dataModel() {
        GenericData model = new GenericData();
        model.addLogicalTypeConversion(new Conversions.DecimalConversion());
        return model;
    }

    private final DatasetSchema schema;
    private final Schema avro;
    private final ParquetWriter<GenericRecord> writer;
    private long rows;

    public ParquetDatasetWriter(Path file, DatasetSchema schema) {
        this.schema = schema;
        this.avro = schema.toAvroSchema();
        try {
            this.writer = AvroParquetWriter.<GenericRecord>builder(new LocalOutputFile(file))
                    .withSchema(avro)
                    .withDataModel(dataModel())
                    .withCompressionCodec(CompressionCodecName.SNAPPY)
                    .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(Map<String, Object> row) {
        GenericRecord rec = new GenericData.Record(avro);
        for (Column c : schema.columns()) {
            if (!row.containsKey(c.name())) {
                throw new IllegalArgumentException(schema.name() + ": missing column " + c.name());
            }
            rec.put(c.name(), convert(c, row.get(c.name())));
        }
        if (row.size() != schema.columns().size()) {
            throw new IllegalArgumentException(schema.name() + ": unexpected columns in " + row.keySet());
        }
        try {
            writer.write(rec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        rows++;
    }

    private Object convert(Column c, Object v) {
        if (v == null) {
            if (!c.nullable()) {
                throw new IllegalArgumentException(schema.name() + "." + c.name() + " is not nullable");
            }
            return null;
        }
        return switch (c.type().kind()) {
            case STRING -> v.toString();
            case INT -> ((Number) v).intValue();
            case LONG -> ((Number) v).longValue();
            case BOOLEAN -> (Boolean) v;
            case DECIMAL -> ((BigDecimal) v).setScale(c.type().scale(), RoundingMode.UNNECESSARY);
        };
    }

    @Override
    public long rowCount() {
        return rows;
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
