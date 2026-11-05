package com.carddemo.online.billpay;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.MessageType;
import com.carddemo.online.common.ScreenResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COBIL00C PROCESS-ENTER-KEY: pay the account balance in full with an online bill-payment transaction. */
@Service
public class BillPayService {

    static final String MSG_ACCT_EMPTY = "Acct ID can NOT be empty...";
    static final String MSG_INVALID_CONFIRM = "Invalid value. Valid values are (Y/N)...";
    static final String MSG_ACCT_NOT_FOUND = "Account ID NOT found...";
    static final String MSG_ACCT_LOOKUP = "Unable to lookup Account...";
    static final String MSG_ACCT_UPDATE = "Unable to Update Account...";
    static final String MSG_XREF_LOOKUP = "Unable to lookup XREF AIX file...";
    static final String MSG_TRAN_NOT_FOUND = "Transaction ID NOT found...";
    static final String MSG_TRAN_LOOKUP = "Unable to lookup Transaction...";
    static final String MSG_TRAN_EXISTS = "Tran ID already exist...";
    static final String MSG_TRAN_ADD = "Unable to Add Bill pay Transaction...";
    static final String MSG_NOTHING_TO_PAY = "You have nothing to pay...";
    static final String MSG_CONFIRM = "Confirm to make a bill payment...";

    static final String TRAN_TYPE_CD = "02";
    static final int TRAN_CAT_CD = 2;
    static final String TRAN_SOURCE = "POS TERM";
    static final String TRAN_DESC = "BILL PAYMENT - ONLINE";
    static final long MERCHANT_ID = 999999999L;
    static final String MERCHANT_NAME = "BILL PAYMENT";
    static final String MERCHANT_CITY = "N/A";
    static final String MERCHANT_ZIP = "N/A";

    static final String FIELD_ACCT_ID = "acctId";
    static final String FIELD_CONFIRM = "confirm";

    private static final Logger log = LoggerFactory.getLogger(BillPayService.class);

    private final BillPayDao dao;
    private final Clock clock;

    public BillPayService(BillPayDao dao, ObjectProvider<Clock> clock) {
        this.dao = dao;
        this.clock = clock.getIfAvailable(Clock::systemDefaultZone);
    }

    /**
     * ENTER on the bill-pay screen. A blank confirm shows the balance and asks for confirmation; Y pays the
     * balance (transaction WRITE + account REWRITE in one database transaction); N clears the screen.
     */
    @Transactional
    public ScreenResponse<BillPayScreen> enter(String acctIdInput, String confirmInput) {
        // PROCESS-ENTER-KEY: ACTIDINI = SPACES OR LOW-VALUES
        if (CobolText.isBlank(acctIdInput)) {
            throw CardDemoException.validation(MSG_ACCT_EMPTY, FIELD_ACCT_ID);
        }
        String acctIdText = CobolText.rtrim(acctIdInput).trim();

        // PROCESS-ENTER-KEY: EVALUATE CONFIRMI
        boolean pay;
        String confirm = CobolText.rtrim(confirmInput);
        switch (confirm) {
            case "Y", "y" -> pay = true;
            case "N", "n" -> {
                // CLEAR-CURRENT-SCREEN: fields and message cleared, cursor on Acct ID
                return new ScreenResponse<>(null, null, null, FIELD_ACCT_ID, null);
            }
            default -> {
                if (!CobolText.isBlank(confirm)) {
                    throw CardDemoException.validation(MSG_INVALID_CONFIRM, FIELD_CONFIRM);
                }
                pay = false;
            }
        }

        long acctId = parseAcctId(acctIdText);
        BigDecimal balance = readAccount(acctId, pay);
        BillPayScreen screen = BillPayScreen.balance(acctIdText, balance);

        // PROCESS-ENTER-KEY: ACCT-CURR-BAL <= ZEROS
        if (balance.signum() <= 0) {
            throw CardDemoException.validation(MSG_NOTHING_TO_PAY, FIELD_ACCT_ID).withData(screen);
        }
        if (!pay) {
            return new ScreenResponse<>(screen, MSG_CONFIRM, MessageType.INFO, FIELD_CONFIRM, null);
        }

        String cardNum = readXref(acctId);
        String tranId = nextTranId();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        writeTransaction(tranId, cardNum, balance, now);
        BigDecimal newBalance = balance.subtract(balance);
        updateAccount(acctId, newBalance);

        // WRITE-TRANSACT-FILE NORMAL: INITIALIZE-ALL-FIELDS, green message
        String message = "Payment successful. " + " Your Transaction ID is " + tranId + ".";
        return new ScreenResponse<>(new BillPayScreen(acctIdText, newBalance, tranId, cardNum, balance), message,
                MessageType.SUCCESS, FIELD_ACCT_ID, null);
    }

    /** ACTIDINI X(11) moved to ACCT-ID 9(11): anything that is not an 11-digit number cannot be found. */
    private static long parseAcctId(String text) {
        if (!CobolText.isNumeric(text) || text.length() > 11) {
            throw CardDemoException.notFound(MSG_ACCT_NOT_FOUND, FIELD_ACCT_ID);
        }
        return Long.parseLong(text);
    }

    /** READ-ACCTDAT-FILE. */
    private BigDecimal readAccount(long acctId, boolean forUpdate) {
        Optional<BigDecimal> balance;
        try {
            balance = dao.readBalance(acctId, forUpdate);
        } catch (DataAccessException e) {
            log.error("READ ACCTDAT failed", e);
            throw CardDemoException.failure(MSG_ACCT_LOOKUP, FIELD_ACCT_ID);
        }
        return balance.orElseThrow(() -> CardDemoException.notFound(MSG_ACCT_NOT_FOUND, FIELD_ACCT_ID));
    }

    /** READ-CXACAIX-FILE. */
    private String readXref(long acctId) {
        Optional<String> cardNum;
        try {
            cardNum = dao.readXrefCardNum(acctId);
        } catch (DataAccessException e) {
            log.error("READ CXACAIX failed", e);
            throw CardDemoException.failure(MSG_XREF_LOOKUP, FIELD_ACCT_ID);
        }
        return cardNum.orElseThrow(() -> CardDemoException.notFound(MSG_ACCT_NOT_FOUND, FIELD_ACCT_ID));
    }

    /**
     * STARTBR-TRANSACT-FILE (HIGH-VALUES) + READPREV-TRANSACT-FILE, then ADD 1 TO WS-TRAN-ID-NUM.
     * STARTBR on an empty KSDS returns NOTFND, so an empty TRANSACT file is reported as such.
     */
    private String nextTranId() {
        Optional<String> last;
        try {
            last = dao.readLastTranId();
        } catch (DataAccessException e) {
            log.error("READPREV TRANSACT failed", e);
            throw CardDemoException.failure(MSG_TRAN_LOOKUP, FIELD_ACCT_ID);
        }
        String lastId = last.orElseThrow(() -> CardDemoException.notFound(MSG_TRAN_NOT_FOUND, FIELD_ACCT_ID));
        String digits = lastId.trim();
        if (!CobolText.isNumeric(digits) || digits.length() > 16) {
            throw CardDemoException.failure(MSG_TRAN_LOOKUP, FIELD_ACCT_ID);
        }
        return String.format("%016d", Long.parseLong(digits) + 1);
    }

    /** WRITE-TRANSACT-FILE. */
    private void writeTransaction(String tranId, String cardNum, BigDecimal amount, LocalDateTime timestamp) {
        try {
            dao.insertTransaction(tranId, cardNum, amount, timestamp);
        } catch (DuplicateKeyException e) {
            throw CardDemoException.conflict(MSG_TRAN_EXISTS, FIELD_ACCT_ID);
        } catch (DataAccessException e) {
            log.error("WRITE TRANSACT failed", e);
            throw CardDemoException.failure(MSG_TRAN_ADD, FIELD_ACCT_ID);
        }
    }

    /** UPDATE-ACCTDAT-FILE (REWRITE). Any failure rolls back the transaction row written before it. */
    private void updateAccount(long acctId, BigDecimal newBalance) {
        int updated;
        try {
            updated = dao.updateBalance(acctId, newBalance);
        } catch (DataAccessException e) {
            log.error("REWRITE ACCTDAT failed", e);
            throw CardDemoException.failure(MSG_ACCT_UPDATE, FIELD_ACCT_ID);
        }
        if (updated == 0) {
            throw CardDemoException.notFound(MSG_ACCT_NOT_FOUND, FIELD_ACCT_ID);
        }
    }
}
