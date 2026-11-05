package com.carddemo.online.card;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.KeysetPage;
import com.carddemo.online.common.MessageType;
import com.carddemo.online.common.ScreenResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** COCRDLIC: credit card list, 7 rows per page, optional account / card filters, S/U row actions. */
@Service
public class CardListService {

    static final int MAX_SCREEN_LINES = 7;
    static final String VIEW_PROGRAM = "COCRDSLC";
    static final String UPDATE_PROGRAM = "COCRDUPC";

    private final JdbcTemplate jdbc;

    public CardListService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Query parameters of one COCRDLIC interaction.
     *
     * @param start      re-display from this card number (GTEQ): ENTER keeps WS-CA-FIRST-CARD-NUM
     * @param after      PF8: last card number of the current page
     * @param before     PF7: first card number of the current page
     * @param page       WS-CA-SCREEN-NUM currently displayed
     * @param selections comma separated select codes for rows 1-7 of the page being re-displayed
     */
    public record ListRequest(String acctId, String cardNum, String start, String after, String before, Integer page,
            String selections) {
    }

    private record Filter(Long acctId, String cardNum) {
    }

    public ScreenResponse<CardListView> list(ListRequest req) {
        int screenNum = req.page() == null || req.page() < 1 ? 1 : req.page();
        Filter filter = editFilters(req);

        if (!CobolText.isBlank(req.before())) {
            if (screenNum <= 1) {
                // 1400-SETUP-MESSAGE: PF7 AND CA-FIRST-PAGE -> re-read the current page
                KeysetPage<CardRow> page = readForward(filter, req.before(), true, 1).page();
                return screen(page, null, CardMessages.NO_PREVIOUS_PAGES);
            }
            return screen(readBackward(filter, req.before(), screenNum - 1), CardMessages.INFORM_REC_ACTIONS, null);
        }

        if (!CobolText.isBlank(req.after())) {
            Forward next = readForward(filter, req.after(), false, screenNum + 1);
            if (next.page().items().isEmpty()) {
                // PF8 AND CA-NEXT-PAGE-NOT-EXISTS -> WHEN OTHER re-reads from WS-CA-FIRST-CARD-NUM
                Forward same = readForward(filter, req.start(), true, screenNum);
                return screen(same.page(), null, CardMessages.NO_MORE_PAGES);
            }
            return forwardScreen(next, screenNum + 1);
        }

        Forward current = readForward(filter, req.start(), true, screenNum);
        if (!CobolText.isBlank(req.selections())) {
            ScreenResponse<CardListView> selected = editSelections(req.selections(), current.page());
            if (selected != null) {
                return selected;
            }
        }
        return forwardScreen(current, screenNum);
    }

    /** 2210-EDIT-ACCOUNT / 2220-EDIT-CARD: optional filters; on error the list is not read. */
    private Filter editFilters(ListRequest req) {
        String message = null;
        String field = null;
        Long acct = null;
        String card = null;
        if (!CardKeys.isBlankOrZero(req.acctId(), 11)) {
            if (CardKeys.isDigits(req.acctId(), 11)) {
                acct = Long.valueOf(req.acctId().trim());
            } else {
                message = CardMessages.LIST_ACCT_FILTER_INVALID;
                field = CardKeys.ACCT_ID;
            }
        }
        if (!CardKeys.isBlankOrZero(req.cardNum(), 16)) {
            if (CardKeys.isDigits(req.cardNum(), 16)) {
                card = req.cardNum().trim();
            } else if (message == null) {
                message = CardMessages.LIST_CARD_FILTER_INVALID;
                field = CardKeys.CARD_NUM;
            }
        }
        if (message != null) {
            KeysetPage<CardRow> empty = new KeysetPage<>(List.of(), Math.max(1, req.page() == null ? 1 : req.page()),
                    null, null, false, false);
            throw CardDemoException.validation(message, field)
                    .withData(new CardListView(empty, null, List.of(), null, null));
        }
        return new Filter(acct, card);
    }

    /** 2250-EDIT-ARRAY: at most one S/U; anything else non-blank is an invalid action code. */
    private ScreenResponse<CardListView> editSelections(String raw, KeysetPage<CardRow> page) {
        String[] codes = raw.split(",", -1);
        int rows = Math.min(page.items().size(), Math.min(codes.length, MAX_SCREEN_LINES));
        List<Integer> actionRows = new ArrayList<>();
        List<Integer> invalidRows = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            String code = codes[i];
            if (CobolText.isBlank(code)) {
                continue;
            }
            String c = code.trim();
            if ("S".equals(c) || "U".equals(c)) {
                actionRows.add(i + 1);
            } else {
                invalidRows.add(i + 1);
            }
        }
        String message = null;
        List<Integer> errorRows = new ArrayList<>(invalidRows);
        if (actionRows.size() > 1) {
            message = CardMessages.MORE_THAN_ONE_ACTION;
            errorRows.addAll(actionRows);
        } else if (!invalidRows.isEmpty()) {
            message = CardMessages.INVALID_ACTION_CODE;
        }
        if (message != null) {
            Collections.sort(errorRows);
            throw CardDemoException.validation(message, "select" + errorRows.get(0))
                    .withData(new CardListView(page, null, errorRows, null, null));
        }
        if (actionRows.isEmpty()) {
            return null;
        }
        int row = actionRows.get(0);
        CardRow card = page.items().get(row - 1);
        String code = codes[row - 1].trim();
        CardListView view = new CardListView(page, null, List.of(), card.acctId(), card.cardNum());
        return ScreenResponse.of(view).withNextProgram("S".equals(code) ? VIEW_PROGRAM : UPDATE_PROGRAM);
    }

    private record Forward(KeysetPage<CardRow> page, boolean endOfFile) {
    }

    /** 9000-READ-FORWARD: STARTBR GTEQ key, READNEXT up to 7 matching rows plus one look-ahead. */
    private Forward readForward(Filter filter, String key, boolean inclusive, int screenNum) {
        StringBuilder sql = new StringBuilder("SELECT card_num, acct_id, active_status FROM card WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!CobolText.isBlank(key)) {
            sql.append(inclusive ? " AND card_num >= ?" : " AND card_num > ?");
            args.add(key.trim());
        }
        appendFilter(sql, args, filter);
        sql.append(" ORDER BY card_num LIMIT ").append(MAX_SCREEN_LINES + 1);
        List<CardRow> rows = new ArrayList<>(jdbc.query(sql.toString(), (rs, n) -> row(rs), args.toArray()));
        boolean more = rows.size() > MAX_SCREEN_LINES;
        if (more) {
            rows = rows.subList(0, MAX_SCREEN_LINES);
        }
        return new Forward(page(rows, screenNum, more), !more);
    }

    /** 9100-READ-BACKWARDS: READPREV the 7 matching rows before the current first row. */
    private KeysetPage<CardRow> readBackward(Filter filter, String key, int screenNum) {
        StringBuilder sql = new StringBuilder("SELECT card_num, acct_id, active_status FROM card WHERE card_num < ?");
        List<Object> args = new ArrayList<>();
        args.add(key.trim());
        appendFilter(sql, args, filter);
        sql.append(" ORDER BY card_num DESC LIMIT ").append(MAX_SCREEN_LINES);
        List<CardRow> rows = new ArrayList<>(jdbc.query(sql.toString(), (rs, n) -> row(rs), args.toArray()));
        Collections.reverse(rows);
        return page(rows, screenNum, true);
    }

    private static void appendFilter(StringBuilder sql, List<Object> args, Filter filter) {
        if (filter.acctId() != null) {
            sql.append(" AND acct_id = ?");
            args.add(filter.acctId());
        }
        if (filter.cardNum() != null) {
            sql.append(" AND card_num = ?");
            args.add(filter.cardNum());
        }
    }

    private static CardRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CardRow(String.format("%011d", rs.getLong("acct_id")), rs.getString("card_num"),
                rs.getString("active_status"));
    }

    private static KeysetPage<CardRow> page(List<CardRow> rows, int screenNum, boolean hasNext) {
        String first = rows.isEmpty() ? null : rows.get(0).cardNum();
        String last = rows.isEmpty() ? null : rows.get(rows.size() - 1).cardNum();
        return new KeysetPage<>(List.copyOf(rows), screenNum, first, last, screenNum > 1, hasNext);
    }

    /** Messages set by 9000-READ-FORWARD when the browse reaches end of file. */
    private ScreenResponse<CardListView> forwardScreen(Forward fwd, int screenNum) {
        if (!fwd.endOfFile()) {
            return screen(fwd.page(), CardMessages.INFORM_REC_ACTIONS, null);
        }
        if (screenNum == 1 && fwd.page().items().isEmpty()) {
            return screen(fwd.page(), null, CardMessages.NO_RECORDS_FOUND);
        }
        return screen(fwd.page(), CardMessages.INFORM_REC_ACTIONS, CardMessages.NO_MORE_RECORDS);
    }

    private static ScreenResponse<CardListView> screen(KeysetPage<CardRow> page, String info, String error) {
        CardListView view = new CardListView(page, info, List.of(), null, null);
        if (error != null) {
            return new ScreenResponse<>(view, error, MessageType.ERROR, null, null);
        }
        return new ScreenResponse<>(view, info, MessageType.INFO, null, null);
    }
}
