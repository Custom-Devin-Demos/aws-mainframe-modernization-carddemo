package com.carddemo.intcalc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Job parameters.
 *
 * @param parmDate            JCL PARM (PARM-DATE, PIC X(10)); prefix of every generated TRAN-ID
 * @param processingTimestamp value written to TRAN-ORIG-TS / TRAN-PROC-TS
 * @param updateLastAccount   CBACT04C never rewrites the final account in the TCATBALF file (its
 *                            end-of-file branch is unreachable). {@code false} reproduces that
 *                            behaviour for parity; {@code true} applies the intended update.
 */
public record JobConfig(String parmDate, LocalDateTime processingTimestamp, boolean updateLastAccount) {

    private static final DateTimeFormatter DB2_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS'0000'");

    public JobConfig {
        if (parmDate == null || parmDate.length() > 10) {
            throw new IllegalArgumentException("parmDate must be at most 10 characters");
        }
    }

    /** Z-GET-DB2-FORMAT-TIMESTAMP: {@code YYYY-MM-DD-HH.MM.SS.hh0000}. */
    public String db2Timestamp() {
        return DB2_TS.format(processingTimestamp);
    }
}
