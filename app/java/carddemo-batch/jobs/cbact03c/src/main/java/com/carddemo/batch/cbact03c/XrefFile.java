package com.carddemo.batch.cbact03c;

/**
 * {@code SELECT XREFFILE-FILE ... ORGANIZATION IS INDEXED ACCESS MODE IS SEQUENTIAL}: the operations
 * CBACT03C performs on DD XREFFILE, each returning the two-character COBOL file status
 * ({@code XREFFILE-STATUS}).
 */
interface XrefFile {

    /** {@code OPEN INPUT XREFFILE-FILE}. */
    String open();

    /** {@code READ XREFFILE-FILE} (next record in key order). On {@code "00"}, {@link #record()} holds the record. */
    String read();

    /** {@code FD-XREFFILE-REC}: the 50-byte image of the last record read, FILLER bytes included. */
    String record();

    /** {@code CLOSE XREFFILE-FILE}. */
    String close();
}
