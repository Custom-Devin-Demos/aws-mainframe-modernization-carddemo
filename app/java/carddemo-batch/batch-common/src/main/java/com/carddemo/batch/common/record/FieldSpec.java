package com.carddemo.batch.common.record;

/**
 * One elementary item of a copybook record.
 *
 * <ul>
 *   <li>{@link Kind#ALPHANUMERIC}: {@code PIC X(n)}, held as the raw space-padded string.</li>
 *   <li>{@link Kind#NUMERIC_CODE}: unsigned integer {@code PIC 9(n)} (ids, codes, keys), held as the
 *       zero-padded digit string so leading zeros and key ordering are preserved.</li>
 *   <li>{@link Kind#DECIMAL}: signed and/or scaled zoned numerics ({@code PIC S9(i)V9(s)}), held as
 *       {@link java.math.BigDecimal} with the picture's scale.</li>
 *   <li>{@link Kind#FILLER}: skipped on read, written as spaces.</li>
 * </ul>
 */
public record FieldSpec(String cobolName, Kind kind, int length, int integerDigits, int scale, boolean signed,
                        String picture) {

    public enum Kind { ALPHANUMERIC, NUMERIC_CODE, DECIMAL, FILLER }

    public static FieldSpec alnum(String cobolName, int length) {
        return new FieldSpec(cobolName, Kind.ALPHANUMERIC, length, 0, 0, false, "X(" + length + ")");
    }

    public static FieldSpec code(String cobolName, int digits) {
        return new FieldSpec(cobolName, Kind.NUMERIC_CODE, digits, digits, 0, false, "9(" + digits + ")");
    }

    public static FieldSpec signedDecimal(String cobolName, int integerDigits, int scale) {
        String pic = "S9(" + integerDigits + ")" + (scale > 0 ? "V9(" + scale + ")" : "");
        return new FieldSpec(cobolName, Kind.DECIMAL, integerDigits + scale, integerDigits, scale, true, pic);
    }

    public static FieldSpec unsignedDecimal(String cobolName, int integerDigits, int scale) {
        String pic = "9(" + integerDigits + ")V9(" + scale + ")";
        return new FieldSpec(cobolName, Kind.DECIMAL, integerDigits + scale, integerDigits, scale, false, pic);
    }

    public static FieldSpec filler(int length) {
        return new FieldSpec("FILLER", Kind.FILLER, length, 0, 0, false, "X(" + length + ")");
    }

    public boolean isFiller() {
        return kind == Kind.FILLER;
    }

    /** Parquet/Databricks column name: COBOL name lower-cased with hyphens as underscores. */
    public String columnName() {
        return cobolName.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }

    /** Total digits for a DECIMAL column (Parquet/Spark precision). */
    public int precision() {
        return integerDigits + scale;
    }
}
