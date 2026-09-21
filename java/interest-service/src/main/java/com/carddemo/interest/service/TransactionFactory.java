package com.carddemo.interest.service;

import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.CardXref;
import com.carddemo.interest.domain.Transaction;
import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 1300-B-WRITE-TX: builds the system interest transaction for one category balance.
 *
 * <ul>
 *   <li>TRAN-ID = PARM-DATE (10) + WS-TRANID-SUFFIX (6 digits, incremented per transaction)</li>
 *   <li>TRAN-TYPE-CD '01', TRAN-CAT-CD '05', TRAN-SOURCE 'System'</li>
 *   <li>TRAN-DESC 'Int. for a/c ' + ACCT-ID</li>
 *   <li>TRAN-AMT = WS-MONTHLY-INT, merchant fields zero/spaces, TRAN-CARD-NUM = XREF-CARD-NUM</li>
 *   <li>TRAN-ORIG-TS = TRAN-PROC-TS = DB2 format timestamp</li>
 * </ul>
 */
@Component
@Trace(program = "CBACT04C", paragraph = "1300-B-WRITE-TX", lines = "473-500")
public class TransactionFactory {

    public static final String INTEREST_TRAN_TYPE_CD = "01";
    public static final String INTEREST_TRAN_CAT_CD = "0005";
    public static final String INTEREST_TRAN_SOURCE = "System";
    public static final String INTEREST_DESC_PREFIX = "Int. for a/c ";
    public static final String ZERO_MERCHANT_ID = "000000000";

    /** WS-TRANID-SUFFIX is PIC 9(06): six digits, high-order digits dropped on overflow. */
    static final long SUFFIX_MODULUS = 1_000_000L;

    /**
     * @param runDate PARM-DATE job parameter (10 chars, e.g. "2022071800")
     * @param suffix  WS-TRANID-SUFFIX after ADD 1 (1-based, formatted 9(06))
     * @param timestamp DB2-FORMAT-TS (26 chars)
     */
    public Transaction interestTransaction(String runDate, long suffix, Account account, CardXref xref,
                                           BigDecimal monthlyInterest, String timestamp) {
        return new Transaction(
                transactionId(runDate, suffix),
                INTEREST_TRAN_TYPE_CD,
                INTEREST_TRAN_CAT_CD,
                INTEREST_TRAN_SOURCE,
                INTEREST_DESC_PREFIX + account.id(),
                monthlyInterest,
                ZERO_MERCHANT_ID,
                "",
                "",
                "",
                xref.cardNum(),
                timestamp,
                timestamp);
    }

    /** STRING PARM-DATE, WS-TRANID-SUFFIX DELIMITED BY SIZE INTO TRAN-ID (X(16)). */
    @Trace(program = "CBACT04C", paragraph = "1300-B-WRITE-TX", lines = "474-480")
    static String transactionId(String runDate, long suffix) {
        if (suffix < 0) {
            throw new IllegalArgumentException("WS-TRANID-SUFFIX is unsigned: " + suffix);
        }
        String parmDate = padRight(runDate, 10);
        return parmDate + String.format("%06d", suffix % SUFFIX_MODULUS);
    }

    private static String padRight(String value, int width) {
        String v = value == null ? "" : value;
        if (v.length() >= width) {
            return v.substring(0, width);
        }
        return v + " ".repeat(width - v.length());
    }
}
