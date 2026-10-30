package com.carddemo.batch.common.job;

import java.util.LinkedHashMap;
import java.util.Map;

/** COBOL RETURN-CODE plus named counters (e.g. transactions processed / rejected). */
public record JobResult(int returnCode, Map<String, Long> counters) {

    public static JobResult ok(Map<String, Long> counters) {
        return new JobResult(0, new LinkedHashMap<>(counters));
    }

    public static JobResult of(int returnCode, Map<String, Long> counters) {
        return new JobResult(returnCode, new LinkedHashMap<>(counters));
    }
}
