package com.carddemo.interest.service;

import com.carddemo.interest.trace.Trace;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Z-GET-DB2-FORMAT-TIMESTAMP: {@code YYYY-MM-DD-HH.MM.SS.mm0000} (26 chars), built from
 * FUNCTION CURRENT-DATE whose fractional part has two digits (hundredths of a second).
 */
@Component
@Trace(program = "CBACT04C", paragraph = "Z-GET-DB2-FORMAT-TIMESTAMP", lines = "613-626")
public class Db2TimestampSupplier implements Supplier<String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS");

    private final Clock clock;

    public Db2TimestampSupplier() {
        this(Clock.systemDefaultZone());
    }

    public Db2TimestampSupplier(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String get() {
        return LocalDateTime.now(clock).format(FORMAT) + "0000";
    }
}
