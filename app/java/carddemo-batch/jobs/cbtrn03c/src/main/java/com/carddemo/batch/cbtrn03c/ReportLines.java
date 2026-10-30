package com.carddemo.batch.cbtrn03c;

import java.math.BigDecimal;

/** CVTRA07Y report line layouts (133-byte TRANREPT records) and their edited pictures. */
final class ReportLines {

    static final int LINE_LENGTH = 133;
    static final BigDecimal S9_9_V99_LIMIT = new BigDecimal("1000000000");
    static final String HEADER_2 = "-".repeat(LINE_LENGTH);
    static final String BLANK_LINE = " ".repeat(LINE_LENGTH);
    static final String HEADER_1 = pad("Transaction ID", 17) + pad("Account ID", 12) + pad("Transaction Type", 19)
            + pad("Tran Category", 35) + pad("Tran Source", 14) + " " + pad("        Amount", 16);

    private ReportLines() {
    }

    /** REPORT-NAME-HEADER with REPT-START-DATE / REPT-END-DATE. */
    static String nameHeader(String startDate, String endDate) {
        return pad("DALYREPT", 38) + pad("Daily Transaction Report", 41) + pad("Date Range: ", 12)
                + pad(startDate, 10) + " to " + pad(endDate, 10);
    }

    /** TRANSACTION-DETAIL-REPORT. */
    static String detail(Detail d) {
        return pad(d.tranId(), 16) + " " + pad(d.accountId(), 11) + " " + pad(d.typeCd(), 2) + "-" + pad(d.typeDesc(), 15)
                + " " + pad(d.catCd(), 4) + "-" + pad(d.catDesc(), 29) + " " + pad(d.source(), 10) + "    "
                + edit(d.amount(), false) + "  ";
    }

    static String pageTotal(BigDecimal total) {
        return pad("Page Total", 11) + ".".repeat(86) + edit(total, true);
    }

    static String accountTotal(BigDecimal total) {
        return pad("Account Total", 13) + ".".repeat(84) + edit(total, true);
    }

    static String grandTotal(BigDecimal total) {
        return pad("Grand Total", 11) + ".".repeat(86) + edit(total, true);
    }

    /** Detail fields after the MOVEs into TRANSACTION-DETAIL-REPORT (already truncated to the picture). */
    record Detail(String tranId, String accountId, String typeCd, String typeDesc, String catCd, String catDesc,
                  String source, BigDecimal amount) {
    }

    /**
     * {@code PIC -ZZZ,ZZZ,ZZZ.ZZ} ({@code plusSign} false) or {@code PIC +ZZZ,ZZZ,ZZZ.ZZ} (true): zero
     * suppression up to the decimal point, commas blanked while suppressing, and an all-space field
     * (sign included) for zero because every digit position is Z.
     */
    static String edit(BigDecimal value, boolean plusSign) {
        long cents = toPicture(value).movePointRight(2).longValueExact();
        if (cents == 0) {
            return " ".repeat(15);
        }
        String digits = String.format("%011d", Math.abs(cents));
        StringBuilder out = new StringBuilder(15);
        out.append(cents < 0 ? '-' : plusSign ? '+' : ' ');
        boolean suppress = true;
        int d = 0;
        for (char t : "999,999,999.99".toCharArray()) {
            if (t == '9') {
                char c = digits.charAt(d++);
                if (suppress && c == '0') {
                    out.append(' ');
                } else {
                    suppress = false;
                    out.append(c);
                }
            } else if (t == ',') {
                out.append(suppress ? ' ' : ',');
            } else {
                suppress = false;
                out.append('.');
            }
        }
        return out.toString();
    }

    /** Result of storing into {@code PIC S9(09)V99} without ON SIZE ERROR: high-order digits dropped. */
    static BigDecimal toPicture(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.DOWN).remainder(S9_9_V99_LIMIT);
    }

    static String pad(String s, int len) {
        return s.length() >= len ? s.substring(0, len) : s + " ".repeat(len - s.length());
    }

    static String toRecord(String line) {
        return pad(line, LINE_LENGTH);
    }
}
