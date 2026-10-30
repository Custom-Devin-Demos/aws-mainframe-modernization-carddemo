package com.carddemo.batch.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.schema.DatasetSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Keeps the checked-in schema contract ({@code src/main/resources/schemas/<dataset>.schema.json})
 * identical to what the job publishes. Run with {@code -Dcarddemo.updateSchemas=true} to regenerate.
 */
public final class SchemaContract {

    private SchemaContract() {
    }

    public static void assertMatches(DatasetSchema schema, Path moduleDir) throws IOException {
        Path file = moduleDir.resolve("src/main/resources/schemas/" + schema.name() + ".schema.json");
        if (Boolean.getBoolean("carddemo.updateSchemas")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, schema.toSparkJson(), StandardCharsets.UTF_8);
        }
        assertThat(file).as("schema contract %s (regenerate with -Dcarddemo.updateSchemas=true)", file).exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(schema.toSparkJson());
    }
}
