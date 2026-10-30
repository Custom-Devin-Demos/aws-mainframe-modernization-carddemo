package com.carddemo.batch.cbact02c;

/**
 * The CARDFILE-FILE as CBACT02C uses it ({@code ORGANIZATION INDEXED, ACCESS SEQUENTIAL}): every
 * operation returns the two-character COBOL file status that lands in {@code CARDFILE-STATUS}.
 */
interface CardFile {

    String OK = "00";
    String END_OF_FILE = "10";

    /** {@code OPEN INPUT CARDFILE-FILE}. */
    String open();

    /** {@code READ CARDFILE-FILE INTO CARD-RECORD}: the next record in key order, or status 10 at end. */
    ReadResult readNext();

    /** {@code CLOSE CARDFILE-FILE}. */
    String close();

    /** Diagnostic detail for the last failed operation (not part of SYSOUT), or null. */
    default String lastError() {
        return null;
    }

    /** File status plus the 150-byte record image moved INTO {@code CARD-RECORD} (null unless status 00). */
    record ReadResult(String status, String recordImage) {

        static ReadResult ok(String recordImage) {
            return new ReadResult(OK, recordImage);
        }

        static ReadResult status(String status) {
            return new ReadResult(status, null);
        }
    }
}
