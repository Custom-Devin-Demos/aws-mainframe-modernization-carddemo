package com.carddemo.batch.common.schema;

/** Logical column types; each maps to one Spark SQL type and one Parquet physical/logical type. */
public record ColumnType(Kind kind, int precision, int scale) {

    public enum Kind { STRING, INT, LONG, DECIMAL, BOOLEAN }

    public static final ColumnType STRING = new ColumnType(Kind.STRING, 0, 0);
    public static final ColumnType INT = new ColumnType(Kind.INT, 0, 0);
    public static final ColumnType LONG = new ColumnType(Kind.LONG, 0, 0);
    public static final ColumnType BOOLEAN = new ColumnType(Kind.BOOLEAN, 0, 0);

    public static ColumnType decimal(int precision, int scale) {
        return new ColumnType(Kind.DECIMAL, precision, scale);
    }

    /** Spark SQL DataType JSON name (as used by {@code StructType.fromJson}). */
    public String sparkType() {
        return switch (kind) {
            case STRING -> "string";
            case INT -> "integer";
            case LONG -> "long";
            case BOOLEAN -> "boolean";
            case DECIMAL -> "decimal(" + precision + "," + scale + ")";
        };
    }
}
