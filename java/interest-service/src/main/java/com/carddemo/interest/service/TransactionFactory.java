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

    /**
     * @param runDate PARM-DATE job parameter (10 chars, e.g. "2022071800")
     * @param suffix  WS-TRANID-SUFFIX after ADD 1 (1-based, formatted 9(06))
     * @param timestamp DB2-FORMAT-TS (26 chars)
     */
    public Transaction interestTransaction(String runDate, long suffix, Account account, CardXref xref,
                                           BigDecimal monthlyInterest, String timestamp) {
        throw new UnsupportedOperationException("Coordinator integration step 1");
    }
}
