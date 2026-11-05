package com.carddemo.online.transaction;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.domain.CardXref;
import com.carddemo.online.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COTRN02C (CT02) - Add Transaction. Validations run in the COBOL order and stop at the first error (every
 * SEND-TRNADD-SCREEN ends with EXEC CICS RETURN). Error responses carry the map as COTRN02C re-sends it.
 */
@Service
public class TransactionAddService {

    static final String MSG_ACCT_NUMERIC = "Account ID must be Numeric...";
    static final String MSG_ACCT_NOT_FOUND = "Account ID NOT found...";
    static final String MSG_ACCT_LOOKUP = "Unable to lookup Acct in XREF AIX file...";
    static final String MSG_CARD_NUMERIC = "Card Number must be Numeric...";
    static final String MSG_CARD_NOT_FOUND = "Card Number NOT found...";
    static final String MSG_CARD_LOOKUP = "Unable to lookup Card # in XREF file...";
    static final String MSG_KEY_REQUIRED = "Account or Card Number must be entered...";
    static final String MSG_TYPE_EMPTY = "Type CD can NOT be empty...";
    static final String MSG_CAT_EMPTY = "Category CD can NOT be empty...";
    static final String MSG_SOURCE_EMPTY = "Source can NOT be empty...";
    static final String MSG_DESC_EMPTY = "Description can NOT be empty...";
    static final String MSG_AMOUNT_EMPTY = "Amount can NOT be empty...";
    static final String MSG_ORIG_EMPTY = "Orig Date can NOT be empty...";
    static final String MSG_PROC_EMPTY = "Proc Date can NOT be empty...";
    static final String MSG_MID_EMPTY = "Merchant ID can NOT be empty...";
    static final String MSG_MNAME_EMPTY = "Merchant Name can NOT be empty...";
    static final String MSG_MCITY_EMPTY = "Merchant City can NOT be empty...";
    static final String MSG_MZIP_EMPTY = "Merchant Zip can NOT be empty...";
    static final String MSG_TYPE_NUMERIC = "Type CD must be Numeric...";
    static final String MSG_CAT_NUMERIC = "Category CD must be Numeric...";
    static final String MSG_AMOUNT_FORMAT = "Amount should be in format -99999999.99";
    static final String MSG_ORIG_FORMAT = "Orig Date should be in format YYYY-MM-DD";
    static final String MSG_PROC_FORMAT = "Proc Date should be in format YYYY-MM-DD";
    static final String MSG_ORIG_INVALID = "Orig Date - Not a valid date...";
    static final String MSG_PROC_INVALID = "Proc Date - Not a valid date...";
    static final String MSG_MID_NUMERIC = "Merchant ID must be Numeric...";
    static final String MSG_CONFIRM = "Confirm to add this transaction...";
    static final String MSG_CONFIRM_INVALID = "Invalid value. Valid values are (Y/N)...";
    static final String MSG_TRAN_LOOKUP = "Unable to lookup Transaction...";
    static final String MSG_DUPLICATE = "Tran ID already exist...";
    static final String MSG_ADD_FAILED = "Unable to Add Transaction...";

    private static final Pattern AMOUNT = Pattern.compile("[-+]\\d{8}\\.\\d{2}");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final TransactionStore store;

    public TransactionAddService(TransactionStore store) {
        this.store = store;
    }

    /** ENTER: PROCESS-ENTER-KEY. */
    @Transactional
    public ScreenResponse<TransactionAdded> add(TransactionForm request) {
        return processEnter(new MapFields(request));
    }

    /** PF5: COPY-LAST-TRAN-DATA, then PROCESS-ENTER-KEY with the copied values. */
    @Transactional
    public ScreenResponse<TransactionAdded> copyLast(TransactionForm request) {
        MapFields map = new MapFields(request);
        validateKeyFields(map);
        Optional<Transaction> last = read(map, MSG_TRAN_LOOKUP, "acctId", store::findLast);
        last.ifPresent(t -> {
            TransactionView v = TransactionViewService.toView(t);
            map.typeCd = v.typeCd();
            map.catCd = v.catCd();
            map.source = v.source();
            map.amount = v.amount();
            map.description = v.description();
            map.origDate = v.origDate();
            map.procDate = v.procDate();
            map.merchantId = v.merchantId();
            map.merchantName = v.merchantName();
            map.merchantCity = v.merchantCity();
            map.merchantZip = v.merchantZip();
        });
        return processEnter(map);
    }

    private ScreenResponse<TransactionAdded> processEnter(MapFields map) {
        validateKeyFields(map);
        validateDataFields(map);
        switch (map.confirm) {
            case "Y", "y" -> {
                return addTransaction(map);
            }
            case "N", "n", "" -> throw error(map, MSG_CONFIRM, "confirm");
            default -> throw error(map, MSG_CONFIRM_INVALID, "confirm");
        }
    }

    /** VALIDATE-INPUT-KEY-FIELDS: account id (via CXACAIX) or card number (via CCXREF). */
    private void validateKeyFields(MapFields map) {
        if (!CobolText.isBlank(map.acctId)) {
            if (!CobolText.isNumeric(map.acctId)) {
                throw error(map, MSG_ACCT_NUMERIC, "acctId");
            }
            long acctId = Long.parseLong(map.acctId.trim());
            map.acctId = TranFormat.zeroPad(acctId, 11);
            // READ-CXACAIX-FILE
            CardXref xref = read(map, MSG_ACCT_LOOKUP, "acctId", () -> store.xrefByAccount(acctId))
                    .orElseThrow(() -> notFound(map, MSG_ACCT_NOT_FOUND, "acctId"));
            map.cardNum = xref.getCardNum();
        } else if (!CobolText.isBlank(map.cardNum)) {
            if (!CobolText.isNumeric(map.cardNum)) {
                throw error(map, MSG_CARD_NUMERIC, "cardNum");
            }
            String cardNum = TranFormat.zeroPad(Long.parseLong(map.cardNum.trim()), 16);
            map.cardNum = cardNum;
            // READ-CCXREF-FILE
            CardXref xref = read(map, MSG_CARD_LOOKUP, "cardNum", () -> store.xrefByCard(cardNum))
                    .orElseThrow(() -> notFound(map, MSG_CARD_NOT_FOUND, "cardNum"));
            map.acctId = TranFormat.zeroPad(xref.getAcctId(), 11);
        } else {
            throw error(map, MSG_KEY_REQUIRED, "acctId");
        }
    }

    /** VALIDATE-INPUT-DATA-FIELDS. */
    private void validateDataFields(MapFields map) {
        requireValue(map, map.typeCd, MSG_TYPE_EMPTY, "typeCd");
        requireValue(map, map.catCd, MSG_CAT_EMPTY, "catCd");
        requireValue(map, map.source, MSG_SOURCE_EMPTY, "source");
        requireValue(map, map.description, MSG_DESC_EMPTY, "description");
        requireValue(map, map.amount, MSG_AMOUNT_EMPTY, "amount");
        requireValue(map, map.origDate, MSG_ORIG_EMPTY, "origDate");
        requireValue(map, map.procDate, MSG_PROC_EMPTY, "procDate");
        requireValue(map, map.merchantId, MSG_MID_EMPTY, "merchantId");
        requireValue(map, map.merchantName, MSG_MNAME_EMPTY, "merchantName");
        requireValue(map, map.merchantCity, MSG_MCITY_EMPTY, "merchantCity");
        requireValue(map, map.merchantZip, MSG_MZIP_EMPTY, "merchantZip");

        if (!CobolText.isNumeric(map.typeCd)) {
            throw error(map, MSG_TYPE_NUMERIC, "typeCd");
        }
        if (!CobolText.isNumeric(map.catCd)) {
            throw error(map, MSG_CAT_NUMERIC, "catCd");
        }
        if (!AMOUNT.matcher(CobolText.rtrim(map.amount)).matches()) {
            throw error(map, MSG_AMOUNT_FORMAT, "amount");
        }
        if (!DATE.matcher(CobolText.rtrim(map.origDate)).matches()) {
            throw error(map, MSG_ORIG_FORMAT, "origDate");
        }
        if (!DATE.matcher(CobolText.rtrim(map.procDate)).matches()) {
            throw error(map, MSG_PROC_FORMAT, "procDate");
        }
        // COMPUTE WS-TRAN-AMT-N = FUNCTION NUMVAL-C(TRNAMTI) / MOVE WS-TRAN-AMT-E TO TRNAMTI
        map.amount = TranFormat.amount(new BigDecimal(CobolText.rtrim(map.amount)));
        // CALL 'CSUTLDTC'
        if (parseDate(map.origDate) == null) {
            throw error(map, MSG_ORIG_INVALID, "origDate");
        }
        if (parseDate(map.procDate) == null) {
            throw error(map, MSG_PROC_INVALID, "procDate");
        }
        if (!CobolText.isNumeric(map.merchantId)) {
            throw error(map, MSG_MID_NUMERIC, "merchantId");
        }
    }

    /** ADD-TRANSACTION: new TRAN-ID = highest TRAN-ID + 1 (STARTBR HIGH-VALUES / READPREV), then WRITE. */
    private ScreenResponse<TransactionAdded> addTransaction(MapFields map) {
        Optional<Transaction> last = read(map, MSG_TRAN_LOOKUP, "acctId", store::findLast);
        long nextId = last.map(t -> Long.parseLong(t.getTranId().trim())).orElse(0L) + 1;
        String tranId = TranFormat.zeroPad(nextId, 16);

        Transaction t = new Transaction();
        t.setTranId(tranId);
        t.setTranTypeCd(CobolText.rtrim(map.typeCd));
        t.setTranCatCd(Integer.parseInt(map.catCd.trim()));
        t.setSource(CobolText.rtrim(map.source));
        t.setDescription(CobolText.rtrim(map.description));
        t.setAmount(new BigDecimal(map.amount));
        t.setCardNum(map.cardNum);
        t.setMerchantId(Long.parseLong(map.merchantId.trim()));
        t.setMerchantName(CobolText.rtrim(map.merchantName));
        t.setMerchantCity(CobolText.rtrim(map.merchantCity));
        t.setMerchantZip(CobolText.rtrim(map.merchantZip));
        t.setOrigTs(parseDate(map.origDate).atStartOfDay());
        t.setProcTs(parseDate(map.procDate).atStartOfDay());
        try {
            store.insert(t);
        } catch (DuplicateKeyException e) {
            throw CardDemoException.conflict(MSG_DUPLICATE, "acctId").withData(map.toForm());
        } catch (DataAccessException e) {
            throw CardDemoException.failure(MSG_ADD_FAILED, "acctId").withData(map.toForm());
        }
        return ScreenResponse.success(new TransactionAdded(tranId),
                "Transaction added successfully.  Your Tran ID is " + tranId + ".").withField("acctId");
    }

    /** CSUTLDTC (CEEDAYS with mask YYYY-MM-DD); null when not a valid calendar date. */
    private static LocalDate parseDate(String value) {
        String v = CobolText.rtrim(value);
        try {
            LocalDate d = LocalDate.parse(v, ISO_DATE);
            return d.getYear() == 0 ? null : d;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void requireValue(MapFields map, String value, String message, String field) {
        if (CobolText.isBlank(value)) {
            throw error(map, message, field);
        }
    }

    private static <T> T read(MapFields map, String message, String field, Supplier<T> io) {
        try {
            return io.get();
        } catch (DataAccessException e) {
            throw CardDemoException.failure(message, field).withData(map.toForm());
        }
    }

    private static CardDemoException error(MapFields map, String message, String field) {
        return CardDemoException.validation(message, field).withData(map.toForm());
    }

    private static CardDemoException notFound(MapFields map, String message, String field) {
        return CardDemoException.notFound(message, field).withData(map.toForm());
    }

    /** COTRN2AI input fields, truncated to their BMS lengths as a 3270 terminal would. */
    private static final class MapFields {
        String acctId;
        String cardNum;
        String typeCd;
        String catCd;
        String source;
        String description;
        String amount;
        String origDate;
        String procDate;
        String merchantId;
        String merchantName;
        String merchantCity;
        String merchantZip;
        String confirm;

        MapFields(TransactionForm f) {
            acctId = TranFormat.fit(f.acctId(), 11);
            cardNum = TranFormat.fit(f.cardNum(), 16);
            typeCd = TranFormat.fit(f.typeCd(), 2);
            catCd = TranFormat.fit(f.catCd(), 4);
            source = TranFormat.fit(f.source(), 10);
            description = TranFormat.fit(f.description(), 60);
            amount = TranFormat.fit(f.amount(), 12);
            origDate = TranFormat.fit(f.origDate(), 10);
            procDate = TranFormat.fit(f.procDate(), 10);
            merchantId = TranFormat.fit(f.merchantId(), 9);
            merchantName = TranFormat.fit(f.merchantName(), 30);
            merchantCity = TranFormat.fit(f.merchantCity(), 25);
            merchantZip = TranFormat.fit(f.merchantZip(), 10);
            confirm = CobolText.isBlank(f.confirm()) ? "" : TranFormat.fit(f.confirm(), 1);
        }

        TransactionForm toForm() {
            return new TransactionForm(acctId, cardNum, typeCd, catCd, source, description, amount, origDate,
                    procDate, merchantId, merchantName, merchantCity, merchantZip, confirm);
        }
    }
}
