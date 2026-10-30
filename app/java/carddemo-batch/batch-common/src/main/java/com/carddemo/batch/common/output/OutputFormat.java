package com.carddemo.batch.common.output;

/** PARQUET for datasets, CSV for human-readable reports. */
public enum OutputFormat {
    PARQUET("parquet"), CSV("csv");

    private final String extension;

    OutputFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }
}
