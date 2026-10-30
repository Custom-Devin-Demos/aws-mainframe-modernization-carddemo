package com.carddemo.batch.common.output;

import com.carddemo.batch.common.record.CobolRecord;
import java.util.Map;

/** Writes rows of one output dataset. Rows are maps keyed by column name. */
public interface DatasetWriter extends AutoCloseable {

    void write(Map<String, Object> row);

    default void write(CobolRecord record) {
        write(record.toColumns());
    }

    long rowCount();

    @Override
    void close();
}
