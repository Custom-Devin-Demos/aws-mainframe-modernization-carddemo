package com.carddemo.creastmt;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes STMTFILE (LRECL 80 text) and HTMLFILE (LRECL 100) exactly as CBSTM03A builds them from
 * STATEMENT-LINES and HTML-LINES. Each fixed-length record is written space-padded to its LRECL and
 * terminated by a newline.
 */
final class StatementWriter implements Closeable {

    static final int STMT_LRECL = 80;
    static final int HTML_LRECL = 100;

    // STATEMENT-LINES constants
    private static final String ST_LINE0 = "*".repeat(31) + "START OF STATEMENT" + "*".repeat(31);
    private static final String ST_LINE5 = "-".repeat(80);
    private static final String ST_LINE6 = " ".repeat(33) + Cobol.pic("Basic Details", 14) + " ".repeat(33);
    private static final String ST_LINE10 = ST_LINE5;
    private static final String ST_LINE11 = " ".repeat(30) + "TRANSACTION SUMMARY " + " ".repeat(30);
    private static final String ST_LINE12 = ST_LINE5;
    private static final String ST_LINE13 =
            "Tran ID         " + Cobol.pic("Tran Details    ", 51) + "  Tran Amount";
    private static final String ST_LINE15 = "*".repeat(32) + "END OF STATEMENT" + "*".repeat(32);

    // HTML-FIXED-LN 88-level values
    private static final String HTML_L01 = "<!DOCTYPE html>";
    private static final String HTML_L02 = "<html lang=\"en\">";
    private static final String HTML_L03 = "<head>";
    private static final String HTML_L04 = "<meta charset=\"utf-8\">";
    private static final String HTML_L05 = "<title>HTML Table Layout</title>";
    private static final String HTML_L06 = "</head>";
    private static final String HTML_L07 = "<body style=\"margin:0px;\">";
    private static final String HTML_L08 =
            "<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">";
    private static final String HTML_LTRS = "<tr>";
    private static final String HTML_LTRE = "</tr>";
    private static final String HTML_LTDE = "</td>";
    private static final String HTML_L10 = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#1d1d96b3;\">";
    private static final String HTML_L15 = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#FFAF33;\">";
    private static final String HTML_L16 = "<p style=\"font-size:16px\">Bank of XYZ</p>";
    private static final String HTML_L17 = "<p>410 Terry Ave N</p>";
    private static final String HTML_L18 = "<p>Seattle WA 99999</p>";
    private static final String HTML_L22_35 = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#f2f2f2;\">";
    private static final String HTML_L30_42 =
            "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#33FFD1; text-align:center;\">";
    private static final String HTML_L31 = "<p style=\"font-size:16px\">Basic Details</p>";
    private static final String HTML_L43 = "<p style=\"font-size:16px\">Transaction Summary</p>";
    private static final String HTML_L47 =
            "<td style=\"width:25%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">";
    private static final String HTML_L48 = "<p style=\"font-size:16px\">Tran ID</p>";
    private static final String HTML_L50 =
            "<td style=\"width:55%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">";
    private static final String HTML_L51 = "<p style=\"font-size:16px\">Tran Details</p>";
    private static final String HTML_L53 =
            "<td style=\"width:20%; padding:0px 5px; background-color:#33FF5E; text-align:right;\">";
    private static final String HTML_L54 = "<p style=\"font-size:16px\">Amount</p>";
    private static final String HTML_L58 =
            "<td style=\"width:25%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">";
    private static final String HTML_L61 =
            "<td style=\"width:55%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">";
    private static final String HTML_L64 =
            "<td style=\"width:20%; padding:0px 5px; background-color:#f2f2f2; text-align:right;\">";
    private static final String HTML_L75 = "<h3>End of Statement</h3>";
    private static final String HTML_L78 = "</table>";
    private static final String HTML_L79 = "</body>";
    private static final String HTML_L80 = "</html>";

    private final BufferedWriter stmt;
    private final BufferedWriter html;

    StatementWriter(Path stmtFile, Path htmlFile) throws IOException {
        this.stmt = Files.newBufferedWriter(stmtFile, StandardCharsets.ISO_8859_1);
        this.html = Files.newBufferedWriter(htmlFile, StandardCharsets.ISO_8859_1);
    }

    /** One complete statement: 5000-CREATE-STATEMENT, 4000-TRNXFILE-GET and 6000-WRITE-TRANS. */
    void writeStatement(CustomerRecord cust, AccountRecord acct, List<TransactionRecord> transactions)
            throws IOException {
        createStatement(cust, acct);
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (TransactionRecord trnx : transactions) {
            writeTrans(trnx);
            // WS-TOTAL-AMT PIC S9(9)V99 COMP-3
            total = Cobol.truncate(total.add(trnx.amt()), 9, 2);
        }
        writeTrailer(total);
    }

    /** 5000-CREATE-STATEMENT */
    private void createStatement(CustomerRecord cust, AccountRecord acct) throws IOException {
        stmtRecord(ST_LINE0);
        writeHtmlHeader(acct);

        String stName = Cobol.stringInto(75,
                Cobol.delimitedBy(cust.firstName(), " "), " ",
                Cobol.delimitedBy(cust.middleName(), " "), " ",
                Cobol.delimitedBy(cust.lastName(), " "), " ");
        String stAdd1 = Cobol.pic(cust.addrLine1(), 50);
        String stAdd2 = Cobol.pic(cust.addrLine2(), 50);
        String stAdd3 = Cobol.stringInto(80,
                Cobol.delimitedBy(cust.addrLine3(), " "), " ",
                Cobol.delimitedBy(cust.addrStateCd(), " "), " ",
                Cobol.delimitedBy(cust.addrCountryCd(), " "), " ",
                Cobol.delimitedBy(cust.addrZip(), " "), " ");
        String stAcctId = Cobol.pic(Cobol.digits(acct.acctId(), 11), 20);
        String stCurrBal = Cobol.editZeroFilled(acct.currBal(), 9);
        String stFicoScore = Cobol.pic(Cobol.digits(cust.ficoCreditScore(), 3), 20);

        writeHtmlNameAddressBasics(stName, stAdd1, stAdd2, stAdd3, stAcctId, stCurrBal, stFicoScore);

        stmtRecord(stName + " ".repeat(5));
        stmtRecord(stAdd1 + " ".repeat(30));
        stmtRecord(stAdd2 + " ".repeat(30));
        stmtRecord(stAdd3);
        stmtRecord(ST_LINE5);
        stmtRecord(ST_LINE6);
        stmtRecord(ST_LINE5);
        stmtRecord("Account ID         :" + stAcctId + " ".repeat(40));
        stmtRecord("Current Balance    :" + stCurrBal + " ".repeat(7) + " ".repeat(40));
        stmtRecord("FICO Score         :" + stFicoScore + " ".repeat(40));
        stmtRecord(ST_LINE10);
        stmtRecord(ST_LINE11);
        stmtRecord(ST_LINE12);
        stmtRecord(ST_LINE13);
        stmtRecord(ST_LINE12);
    }

    /** 5100-WRITE-HTML-HEADER */
    private void writeHtmlHeader(AccountRecord acct) throws IOException {
        htmlRecords(HTML_L01, HTML_L02, HTML_L03, HTML_L04, HTML_L05, HTML_L06, HTML_L07, HTML_L08,
                HTML_LTRS, HTML_L10);
        // HTML-L11
        htmlRecord("<h3>Statement for Account Number: "
                + Cobol.pic(Cobol.digits(acct.acctId(), 11), 20) + "</h3>");
        htmlRecords(HTML_LTDE, HTML_LTRE, HTML_LTRS, HTML_L15, HTML_L16, HTML_L17, HTML_L18,
                HTML_LTDE, HTML_LTRE, HTML_LTRS, HTML_L22_35);
    }

    /** 5200-WRITE-HTML-NMADBS */
    private void writeHtmlNameAddressBasics(String stName, String stAdd1, String stAdd2, String stAdd3,
            String stAcctId, String stCurrBal, String stFicoScore) throws IOException {
        String l23Name = Cobol.pic(stName, 50);
        htmlRecord(Cobol.stringInto(HTML_LRECL,
                "<p style=\"font-size:16px\">", Cobol.delimitedBy(l23Name, "  "), "  ", "</p>"));
        for (String addr : List.of(stAdd1, stAdd2, stAdd3)) {
            htmlRecord(Cobol.stringInto(HTML_LRECL, "<p>", Cobol.delimitedBy(addr, "  "), "  ", "</p>"));
        }
        htmlRecords(HTML_LTDE, HTML_LTRE, HTML_LTRS, HTML_L30_42, HTML_L31, HTML_LTDE, HTML_LTRE,
                HTML_LTRS, HTML_L22_35);

        htmlRecord(Cobol.stringInto(HTML_LRECL, "<p>Account ID         : ", stAcctId, "</p>"));
        htmlRecord(Cobol.stringInto(HTML_LRECL, "<p>Current Balance    : ", stCurrBal, "</p>"));
        htmlRecord(Cobol.stringInto(HTML_LRECL, "<p>FICO Score         : ", stFicoScore, "</p>"));
        htmlRecords(HTML_LTDE, HTML_LTRE, HTML_LTRS, HTML_L30_42, HTML_L43, HTML_LTDE, HTML_LTRE,
                HTML_LTRS, HTML_L47, HTML_L48, HTML_LTDE, HTML_L50, HTML_L51, HTML_LTDE, HTML_L53,
                HTML_L54, HTML_LTDE, HTML_LTRE);
    }

    /** 6000-WRITE-TRANS */
    private void writeTrans(TransactionRecord trnx) throws IOException {
        String stTranId = trnx.id();
        String stTranDt = Cobol.pic(trnx.desc(), 49);
        String stTranAmt = Cobol.editZeroSuppressed(trnx.amt(), 9);
        stmtRecord(stTranId + " " + stTranDt + "$" + stTranAmt);

        htmlRecords(HTML_LTRS, HTML_L58);
        htmlRecord(Cobol.stringInto(HTML_LRECL, "<p>", stTranId, "</p>"));
        htmlRecords(HTML_LTDE, HTML_L61);
        htmlRecord(Cobol.stringInto(HTML_LRECL, "<p>", stTranDt, "</p>"));
        htmlRecords(HTML_LTDE, HTML_L64);
        htmlRecord(Cobol.stringInto(HTML_LRECL, "<p>", stTranAmt, "</p>"));
        htmlRecords(HTML_LTDE, HTML_LTRE);
    }

    /** Tail of 4000-TRNXFILE-GET: totals line and closing HTML. */
    private void writeTrailer(BigDecimal totalAmt) throws IOException {
        stmtRecord(ST_LINE12);
        stmtRecord("Total EXP:" + " ".repeat(56) + "$" + Cobol.editZeroSuppressed(totalAmt, 9));
        stmtRecord(ST_LINE15);
        htmlRecords(HTML_LTRS, HTML_L10, HTML_L75, HTML_LTDE, HTML_LTRE, HTML_L78, HTML_L79, HTML_L80);
    }

    private void stmtRecord(String line) throws IOException {
        stmt.write(Cobol.pic(line, STMT_LRECL));
        stmt.write('\n');
    }

    private void htmlRecord(String line) throws IOException {
        html.write(Cobol.pic(line, HTML_LRECL));
        html.write('\n');
    }

    private void htmlRecords(String... lines) throws IOException {
        for (String line : lines) {
            htmlRecord(line);
        }
    }

    @Override
    public void close() throws IOException {
        try (stmt; html) {
            stmt.flush();
            html.flush();
        }
    }
}
