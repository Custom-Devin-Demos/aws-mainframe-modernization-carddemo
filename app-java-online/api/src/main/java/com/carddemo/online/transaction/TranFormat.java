package com.carddemo.online.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Display formats of the COTRN0x maps (PIC edit masks and field truncation). */
final class TranFormat {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter MM_DD_YY = DateTimeFormatter.ofPattern("MM/dd/yy");

    private TranFormat() {
    }

    /** WS-TRAN-AMT PIC +99999999.99, e.g. {@code -00000012.34}. */
    static String amount(BigDecimal amount) {
        BigDecimal v = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.DOWN);
        String digits = v.abs().movePointRight(2).toBigInteger().toString();
        digits = digits.length() > 10 ? digits.substring(digits.length() - 10) : "0".repeat(10 - digits.length()) + digits;
        return (v.signum() < 0 ? "-" : "+") + digits.substring(0, 8) + "." + digits.substring(8);
    }

    /** TRAN-ORIG-TS / TRAN-PROC-TS X(26) text, {@code YYYY-MM-DD HH:MM:SS.ffffff}. */
    static String timestamp(LocalDateTime ts) {
        return ts == null ? "" : TS.format(ts);
    }

    /** POPULATE-TRAN-DATA: TRAN-ORIG-TS rearranged to WS-CURDATE-MM-DD-YY. */
    static String mmddyy(LocalDateTime ts) {
        return ts == null ? "" : MM_DD_YY.format(ts);
    }

    /** MOVE of a longer alphanumeric item into a BMS field of {@code length} characters. */
    static String fit(String value, int length) {
        if (value == null) {
            return "";
        }
        return value.length() > length ? value.substring(0, length) : value;
    }

    /** MOVE of an unsigned numeric item to a PIC 9(n) display field. */
    static String zeroPad(long value, int length) {
        String s = Long.toString(Math.abs(value));
        return s.length() >= length ? s.substring(s.length() - length) : "0".repeat(length - s.length()) + s;
    }
}
