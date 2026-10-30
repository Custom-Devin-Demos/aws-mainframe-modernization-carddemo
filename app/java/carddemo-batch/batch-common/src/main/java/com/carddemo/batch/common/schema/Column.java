package com.carddemo.batch.common.schema;

import java.util.Map;

/** A dataset column. {@code metadata} carries lineage (copybook field, PIC, offset) into the schema file. */
public record Column(String name, ColumnType type, boolean nullable, String description, Map<String, Object> metadata) {

    public static Column of(String name, ColumnType type, String description) {
        return new Column(name, type, false, description, Map.of());
    }

    public static Column nullable(String name, ColumnType type, String description) {
        return new Column(name, type, true, description, Map.of());
    }
}
