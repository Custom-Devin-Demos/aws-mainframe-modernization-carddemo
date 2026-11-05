package com.carddemo.online.card;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.MessageType;
import com.carddemo.online.common.ScreenResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** COCRDUPC: edit embossed name, active status and expiry month/year of one card. */
@Service
public class CardUpdateService {

    static final String SHOW_DETAILS = "SHOW_DETAILS";
    static final String CHANGES_NOT_OK = "CHANGES_NOT_OK";
    static final String CHANGES_OK_NOT_CONFIRMED = "CHANGES_OK_NOT_CONFIRMED";
    static final String CHANGES_OKAYED_AND_DONE = "CHANGES_OKAYED_AND_DONE";
    static final String CHANGES_FAILED = "CHANGES_FAILED";

    static final String NAME = "embossedName";
    static final String STATUS = "activeStatus";
    static final String MONTH = "expiryMonth";
    static final String YEAR = "expiryYear";

    private final CardDetailService details;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public CardUpdateService(CardDetailService details, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.details = details;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    private record Values(String name, String status, String month, String year) {
    }

    private record DbCard(String name, String status, LocalDate expiry) {
    }

    public ScreenResponse<CardUpdateView> update(String cardNum, CardUpdateRequest req) {
        String acct = req.acctId() == null ? "" : req.acctId().trim();
        if (req.original() == null) {
            // CCUP-DETAILS-NOT-FETCHED: 9000-READ-DATA with the search keys
            ScreenResponse<CardDetailView> found = searchForUpdate(acct, cardNum);
            CardDetailView card = found.data();
            return ScreenResponse.of(new CardUpdateView(card, original(card), SHOW_DETAILS), found.message());
        }
        CardUpdateRequest.Original old = req.original();
        Values v = new Values(CobolText.rtrim(req.embossedName()), trim(req.activeStatus()),
                normaliseMonth(req.expiryMonth()), trim(req.expiryYear()));
        CardDetailView shown = new CardDetailView(acct, trim(cardNum), v.name(), v.status(), v.month(), v.year(),
                old.expiryDay(), CardMessages.PROMPT_FOR_CHANGES);
        CardUpdateView notOk = new CardUpdateView(shown, old, CHANGES_NOT_OK);

        // 1200-EDIT-MAP-INPUTS: search keys are re-edited because the API is stateless
        CardKeys.validate(acct, cardNum, notOk);

        // 1200-EDIT-MAP-INPUTS: FUNCTION UPPER-CASE(CCUP-NEW-CARDDATA) = UPPER-CASE(CCUP-OLD-CARDDATA)
        if (sameCardData(v, old)) {
            CardDetailView same = withInfo(shown, CardMessages.FOUND_CARD_FOR_UPDATE);
            throw CardDemoException.validation(CardMessages.NO_CHANGES_DETECTED, NAME)
                    .withData(new CardUpdateView(same, old, SHOW_DETAILS));
        }

        String[] error = editFields(v);
        if (error != null) {
            throw CardDemoException.validation(error[0], error[1]).withData(notOk);
        }

        if (!req.confirm()) {
            // CCUP-CHANGES-OK-NOT-CONFIRMED: wait for PF5
            CardDetailView ok = withInfo(shown, CardMessages.PROMPT_FOR_CONFIRMATION);
            return ScreenResponse.of(new CardUpdateView(ok, old, CHANGES_OK_NOT_CONFIRMED),
                    CardMessages.PROMPT_FOR_CONFIRMATION);
        }
        return write(trim(cardNum), acct, v, old, shown);
    }

    private ScreenResponse<CardDetailView> searchForUpdate(String acct, String cardNum) {
        try {
            return details.find(acct, cardNum, true);
        } catch (CardDemoException e) {
            CardDetailView keys = (CardDetailView) e.getData();
            throw new CardDemoException(e.getStatus(), e.getMessage(), e.getField(),
                    new CardUpdateView(keys, null, "DETAILS_NOT_FETCHED"));
        }
    }

    /** 1230-EDIT-NAME, 1240-EDIT-CARDSTATUS, 1250-EDIT-EXPIRY-MON, 1260-EDIT-EXPIRY-YEAR (first error wins). */
    private static String[] editFields(Values v) {
        String nameError = null;
        if (isBlankOrZeros(v.name())) {
            nameError = CardMessages.PROMPT_FOR_NAME;
        } else if (!v.name().chars().allMatch(c -> c == ' ' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) {
            nameError = CardMessages.NAME_MUST_BE_ALPHA;
        }
        if (nameError != null) {
            return new String[] {nameError, NAME};
        }
        if (isBlankOrZeros(v.status()) || !("Y".equals(v.status()) || "N".equals(v.status()))) {
            return new String[] {CardMessages.STATUS_MUST_BE_YES_NO, STATUS};
        }
        if (isBlankOrZeros(v.month()) || !CobolText.isNumeric(v.month()) || v.month().length() != 2
                || Integer.parseInt(v.month()) < 1 || Integer.parseInt(v.month()) > 12) {
            return new String[] {CardMessages.EXPIRY_MONTH_NOT_VALID, MONTH};
        }
        if (isBlankOrZeros(v.year()) || !CobolText.isNumeric(v.year()) || v.year().length() != 4
                || Integer.parseInt(v.year()) < 1950 || Integer.parseInt(v.year()) > 2099) {
            return new String[] {CardMessages.EXPIRY_YEAR_NOT_VALID, YEAR};
        }
        return null;
    }

    /** 9200-WRITE-PROCESSING. */
    private ScreenResponse<CardUpdateView> write(String cardNum, String acct, Values v,
            CardUpdateRequest.Original old, CardDetailView shown) {
        CardUpdateView failed = new CardUpdateView(withInfo(shown, CardMessages.INFORM_FAILURE), old, CHANGES_FAILED);
        DbCard current;
        try {
            current = tx.execute(status -> {
                // EXEC CICS READ ... UPDATE
                List<DbCard> locked = jdbc.query(
                        "SELECT embossed_name, active_status, expiration_date FROM card WHERE card_num = ? FOR UPDATE",
                        (rs, n) -> new DbCard(rs.getString(1), rs.getString(2),
                                rs.getObject(3, LocalDate.class)),
                        cardNum);
                if (locked.isEmpty()) {
                    return null;
                }
                DbCard db = locked.get(0);
                // 9300-CHECK-CHANGE-IN-REC
                if (!sameAsOld(db, old)) {
                    return db;
                }
                int day = parseDay(old.expiryDay());
                YearMonth ym = YearMonth.of(Integer.parseInt(v.year()), Integer.parseInt(v.month()));
                LocalDate expiry = ym.atDay(Math.min(Math.max(day, 1), ym.lengthOfMonth()));
                // EXEC CICS REWRITE (CVV and account id are kept as stored)
                jdbc.update("UPDATE card SET embossed_name = ?, active_status = ?, expiration_date = ? WHERE card_num = ?",
                        v.name(), v.status(), expiry, cardNum);
                return new DbCard(v.name(), v.status(), expiry);
            });
        } catch (DataAccessException e) {
            // LOCKED-BUT-UPDATE-FAILED
            throw new CardDemoException(HttpStatus.INTERNAL_SERVER_ERROR, CardMessages.UPDATE_FAILED, null, failed);
        }
        if (current == null) {
            // COULD-NOT-LOCK-FOR-UPDATE
            throw CardDemoException.conflict(CardMessages.COULD_NOT_LOCK, null).withData(failed);
        }
        if (!v.name().equals(current.name()) || !v.status().equals(current.status())
                || current.expiry().getYear() != Integer.parseInt(v.year())
                || current.expiry().getMonthValue() != Integer.parseInt(v.month())) {
            // DATA-WAS-CHANGED-BEFORE-UPDATE: show the record as it is now
            CardDetailView fresh = new CardDetailView(acct, cardNum, upper(current.name()), current.status(),
                    String.format("%02d", current.expiry().getMonthValue()),
                    String.format("%04d", current.expiry().getYear()),
                    String.format("%02d", current.expiry().getDayOfMonth()), CardMessages.FOUND_CARD_FOR_UPDATE);
            throw CardDemoException.conflict(CardMessages.DATA_CHANGED_BEFORE_UPDATE, NAME)
                    .withData(new CardUpdateView(fresh, original(fresh), SHOW_DETAILS));
        }
        CardDetailView done = new CardDetailView(acct, cardNum, v.name(), v.status(), v.month(), v.year(),
                String.format("%02d", current.expiry().getDayOfMonth()), CardMessages.CONFIRM_UPDATE_SUCCESS);
        return new ScreenResponse<>(new CardUpdateView(done, original(done), CHANGES_OKAYED_AND_DONE),
                CardMessages.CONFIRM_UPDATE_SUCCESS, MessageType.SUCCESS, null, null);
    }

    private static boolean sameAsOld(DbCard db, CardUpdateRequest.Original old) {
        LocalDate e = db.expiry();
        return upper(db.name()).equals(upper(CobolText.rtrim(old.embossedName())))
                && upper(db.status()).equals(upper(trim(old.activeStatus())))
                && e != null
                && String.format("%04d", e.getYear()).equals(trim(old.expiryYear()))
                && String.format("%02d", e.getMonthValue()).equals(normaliseMonth(old.expiryMonth()))
                && e.getDayOfMonth() == parseDay(old.expiryDay());
    }

    private static boolean sameCardData(Values v, CardUpdateRequest.Original old) {
        return upper(v.name()).equals(upper(CobolText.rtrim(old.embossedName())))
                && upper(v.status()).equals(upper(trim(old.activeStatus())))
                && upper(v.month()).equals(upper(normaliseMonth(old.expiryMonth())))
                && upper(v.year()).equals(upper(trim(old.expiryYear())));
    }

    private static CardUpdateRequest.Original original(CardDetailView c) {
        return new CardUpdateRequest.Original(c.embossedName(), c.activeStatus(), c.expiryYear(), c.expiryMonth(),
                c.expiryDay());
    }

    private static CardDetailView withInfo(CardDetailView c, String info) {
        return new CardDetailView(c.acctId(), c.cardNum(), c.embossedName(), c.activeStatus(), c.expiryMonth(),
                c.expiryYear(), c.expiryDay(), info);
    }

    private static boolean isBlankOrZeros(String value) {
        return CobolText.isBlank(value) || value.trim().chars().allMatch(c -> c == '0');
    }

    /** A one-digit month typed into the 2-byte EXPMON field is treated as zero-padded. */
    private static String normaliseMonth(String month) {
        String m = trim(month);
        return m.length() == 1 && Character.isDigit(m.charAt(0)) ? "0" + m : m;
    }

    private static int parseDay(String day) {
        String d = trim(day);
        return CobolText.isNumeric(d) ? Integer.parseInt(d) : 1;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String upper(String value) {
        return value == null ? "" : CobolText.rtrim(value).toUpperCase(Locale.ROOT);
    }
}
