package com.carddemo.online.report;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * CSUTLDTC: validates a 'YYYY-MM-DD' date with the LE CEEDAYS service and returns the severity and message
 * number of its feedback code. CORPT00C rejects a date unless the severity is 0000 or the message number is
 * 2513 (CEE2513 "unsupported range": a real date before the Lilian epoch 1582-10-15).
 */
final class CsutldtcDateValidator {

    static final String MSG_OK = "0000";
    static final String MSG_BAD_DATE_VALUE = "2508";
    static final String MSG_UNSUPPORTED_RANGE = "2513";
    static final String MSG_INVALID_MONTH = "2517";
    static final String MSG_NON_NUMERIC = "2520";
    static final String MSG_YEAR_IN_ERA_ZERO = "2521";

    private static final LocalDate LILIAN_DAY_ONE = LocalDate.of(1582, 10, 15);

    private CsutldtcDateValidator() {
    }

    /** Returns the CEEDAYS message number for {@code yyyy-mm-dd} ({@link #MSG_OK} when valid). */
    static String check(String date) {
        if (date == null || date.length() != 10 || date.charAt(4) != '-' || date.charAt(7) != '-') {
            return MSG_NON_NUMERIC;
        }
        String yyyy = date.substring(0, 4);
        String mm = date.substring(5, 7);
        String dd = date.substring(8, 10);
        if (!digits(yyyy) || !digits(mm) || !digits(dd)) {
            return MSG_NON_NUMERIC;
        }
        int year = Integer.parseInt(yyyy);
        int month = Integer.parseInt(mm);
        int day = Integer.parseInt(dd);
        if (month < 1 || month > 12) {
            return MSG_INVALID_MONTH;
        }
        if (year == 0) {
            return MSG_YEAR_IN_ERA_ZERO;
        }
        LocalDate parsed;
        try {
            parsed = LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return MSG_BAD_DATE_VALUE;
        }
        return parsed.isBefore(LILIAN_DAY_ONE) ? MSG_UNSUPPORTED_RANGE : MSG_OK;
    }

    /** CORPT00C: {@code CSUTLDTC-RESULT-SEV-CD = '0000'} or {@code CSUTLDTC-RESULT-MSG-NUM = '2513'}. */
    static boolean acceptedByCorpt00c(String date) {
        String msg = check(date);
        return MSG_OK.equals(msg) || MSG_UNSUPPORTED_RANGE.equals(msg);
    }

    private static boolean digits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
