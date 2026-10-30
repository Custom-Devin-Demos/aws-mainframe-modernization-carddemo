package com.carddemo.batch.cbact01c;

/**
 * Port of the assembler date-format routine {@code app/asm/COBDATFT.asm} (DSECT COCDATFT).
 * Only the bytes the assembler stores are changed; everything else in the parameter block,
 * including the tail of {@code CODATECN-0UT-DATE}, keeps its previous content.
 */
final class Cobdatft {
    static final String INVALID_INPUT = "INVALID INPUT";

    private Cobdatft() {
    }

    /** {@code CALL 'COBDATFT' USING CODATECN-REC}. Always returns 0 ({@code SR R15,R15}). */
    static int call(Codatecn rec) {
        String in = rec.inpDate;
        switch (rec.type) {
            // VALIDIN1: YYYYMMDD in -> YYYY-MM-DD out
            case '1' -> {
                if (in.charAt(4) == '-' || rec.outType == '2') {
                    gotoErr(rec);
                } else {
                    rec.outDate = in.substring(0, 4) + '-' + in.substring(4, 6) + '-' + in.substring(6, 8)
                            + rec.outDate.substring(10);
                }
            }
            // VALIDIN2: YYYY-MM-DD in -> YYYYMMDD out (the separators are not validated)
            case '2' -> {
                if (rec.outType == '1') {
                    gotoErr(rec);
                } else {
                    rec.outDate = in.substring(0, 4) + in.substring(5, 7) + in.substring(8, 10) + rec.outDate.substring(8);
                }
            }
            default -> gotoErr(rec);
        }
        return 0;
    }

    // GOTOERR
    private static void gotoErr(Codatecn rec) {
        rec.errorMsg = Codatecn.fit(INVALID_INPUT, Codatecn.ERROR_MSG_LENGTH);
    }
}
