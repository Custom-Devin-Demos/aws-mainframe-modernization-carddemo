package com.carddemo.batch.testkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Locates {@code app/data/ASCII} (set by the parent POM as system property {@code carddemo.sampleData}). */
public final class SampleData {

    private SampleData() {
    }

    public static Path dir() {
        String prop = System.getProperty("carddemo.sampleData");
        if (prop != null && Files.isDirectory(Paths.get(prop))) {
            return Paths.get(prop).toAbsolutePath().normalize();
        }
        for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path candidate = p.resolve("app/data/ASCII");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("cannot locate app/data/ASCII; set -Dcarddemo.sampleData");
    }

    /** e.g. {@code SampleData.file("acctdata.txt")}. */
    public static Path file(String name) {
        Path p = dir().resolve(name);
        if (!Files.isRegularFile(p)) {
            throw new IllegalStateException("missing sample file " + p);
        }
        return p;
    }
}
