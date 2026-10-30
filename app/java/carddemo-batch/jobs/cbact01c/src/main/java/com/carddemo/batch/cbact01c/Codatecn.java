package com.carddemo.batch.cbact01c;

/**
 * Copybook CODATECN ({@code CODATECN-REC}, 80 bytes): the parameter block passed to
 * {@link Cobdatft}. Mutable like the WORKING-STORAGE item it replaces; fields keep their
 * picture lengths. GnuCOBOL initialises the item to spaces, so does this class.
 */
final class Codatecn {
    static final int DATE_LENGTH = 20;
    static final int ERROR_MSG_LENGTH = 38;

    /** CODATECN-TYPE: '1' = YYYYMMDD in, '2' = YYYY-MM-DD in. */
    char type = ' ';
    /** CODATECN-INP-DATE, PIC X(20). */
    String inpDate = " ".repeat(DATE_LENGTH);
    /** CODATECN-OUTTYPE: '1' = YYYY-MM-DD out, '2' = YYYYMMDD out. */
    char outType = ' ';
    /** CODATECN-0UT-DATE, PIC X(20). */
    String outDate = " ".repeat(DATE_LENGTH);
    /** CODATECN-ERROR-MSG, PIC X(38). */
    String errorMsg = " ".repeat(ERROR_MSG_LENGTH);

    /** MOVE of an alphanumeric to CODATECN-INP-DATE (pad / truncate to 20). */
    void moveToInpDate(String value) {
        inpDate = fit(value, DATE_LENGTH);
    }

    static String fit(String s, int len) {
        return s.length() >= len ? s.substring(0, len) : s + " ".repeat(len - s.length());
    }
}
