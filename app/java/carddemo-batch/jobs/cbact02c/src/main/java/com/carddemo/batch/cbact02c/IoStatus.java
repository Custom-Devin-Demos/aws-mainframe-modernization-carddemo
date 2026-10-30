package com.carddemo.batch.cbact02c;

import java.nio.charset.Charset;

/**
 * {@code 9910-DISPLAY-IO-STATUS}: renders a two-character file status as the four characters of
 * {@code IO-STATUS-04} shown after {@code 'FILE STATUS IS: NNNN'}.
 *
 * <ul>
 *   <li>Numeric status not starting with 9: {@code '00'} followed by the status ({@code 35} → {@code 0035}).</li>
 *   <li>Non-numeric, or first character {@code 9} (implementor-defined VSAM statuses): the first character,
 *       then the code point of the second character in the program's code page as three digits
 *       ({@code TWO-BYTES-BINARY} with only its low byte set, moved to {@code PIC 999}). On z/OS that code
 *       page is EBCDIC, so {@code 93} → {@code 9243}; under an ASCII runtime (GnuCOBOL) it is {@code 9051}.</li>
 * </ul>
 */
final class IoStatus {

    /** The code page of the z/OS program the job replaces. */
    static final Charset EBCDIC = Charset.forName("IBM037");

    private IoStatus() {
    }

    static String displayLine(String ioStatus) {
        return "FILE STATUS IS: NNNN" + ioStatus04(ioStatus, EBCDIC);
    }

    // 9910-DISPLAY-IO-STATUS
    static String ioStatus04(String ioStatus, Charset codePage) {
        if (ioStatus.length() != 2) {
            throw new IllegalArgumentException("file status must be 2 characters: '" + ioStatus + "'");
        }
        char stat1 = ioStatus.charAt(0);
        char stat2 = ioStatus.charAt(1);
        if (!isNumeric(ioStatus) || stat1 == '9') {
            int twoBytesBinary = codePage.encode(String.valueOf(stat2)).get(0) & 0xFF;
            return stat1 + String.format("%03d", twoBytesBinary);
        }
        return "00" + ioStatus;
    }

    private static boolean isNumeric(String s) {
        return s.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
