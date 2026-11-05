package com.carddemo.online.transaction;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.KeysetPage;
import com.carddemo.online.common.MessageType;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.domain.Transaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * COTRN00C (CT00) - List Transactions. Ten rows per page; ENTER browses from the search key, PF8/PF7 page
 * forward/backward from the last/first key of the current page. A response without {@code data} means the
 * COBOL program re-sent the map without new rows (the current rows stay on screen).
 */
@Service
public class TransactionListService {

    static final int PAGE_SIZE = 10;

    static final String MSG_INVALID_SELECTION = "Invalid selection. Valid value is S";
    static final String MSG_TRAN_ID_NUMERIC = "Tran ID must be Numeric ...";
    static final String MSG_ALREADY_TOP = "You are already at the top of the page...";
    static final String MSG_ALREADY_BOTTOM = "You are already at the bottom of the page...";
    static final String MSG_AT_TOP = "You are at the top of the page...";
    static final String MSG_REACHED_BOTTOM = "You have reached the bottom of the page...";
    static final String MSG_REACHED_TOP = "You have reached the top of the page...";
    static final String MSG_UNABLE_LOOKUP = "Unable to lookup transaction...";

    static final String FIELD = "startTranId";

    private final TransactionStore store;

    public TransactionListService(TransactionStore store) {
        this.store = store;
    }

    /** PROCESS-ENTER-KEY: row selection, then browse forward from the search Tran ID. */
    public ScreenResponse<KeysetPage<TransactionListRow>> enter(String startTranId, String selection,
            String selectedTranId) {
        String message = null;
        if (!CobolText.isBlank(selection) && !CobolText.isBlank(selectedTranId)) {
            if ("S".equals(selection) || "s".equals(selection)) {
                return new ScreenResponse<KeysetPage<TransactionListRow>>(null, null, null, null, null)
                        .withNextProgram("COTRN01C");
            }
            message = MSG_INVALID_SELECTION;
        }
        String selectionMessage = message;
        String key;
        if (CobolText.isBlank(startTranId)) {
            key = "";
        } else if (CobolText.isNumeric(startTranId)) {
            key = startTranId.trim();
        } else {
            throw CardDemoException.validation(MSG_TRAN_ID_NUMERIC, FIELD);
        }
        return lookup(() -> forward(key, false, 0, selectionMessage));
    }

    /** PROCESS-PF8-KEY. NEXT-PAGE-YES is re-derived from whether any record follows {@code lastKey}. */
    public ScreenResponse<KeysetPage<TransactionListRow>> pageForward(String lastKey, int page) {
        return lookup(() -> {
            boolean nextPage = !CobolText.isBlank(lastKey) && store.existsAfter(lastKey);
            if (!nextPage) {
                return info(null, MSG_ALREADY_BOTTOM);
            }
            return forward(lastKey, true, page, null);
        });
    }

    /** PROCESS-PF7-KEY. */
    public ScreenResponse<KeysetPage<TransactionListRow>> pageBackward(String firstKey, int page) {
        if (page <= 1) {
            return info(null, MSG_ALREADY_TOP);
        }
        return lookup(() -> backward(CobolText.isBlank(firstKey) ? "" : firstKey, page));
    }

    /** PROCESS-PAGE-FORWARD. {@code skipFirst} is the extra READNEXT done for PF8. */
    private ScreenResponse<KeysetPage<TransactionListRow>> forward(String key, boolean skipFirst, int page,
            String message) {
        List<Transaction> records = new ArrayList<>(store.readFrom(key, PAGE_SIZE + 2));
        if (records.isEmpty()) {
            // STARTBR-TRANSACT-FILE: NOTFND
            return info(null, MSG_AT_TOP);
        }
        if (skipFirst) {
            records.remove(0);
        }
        List<Transaction> rows = records.subList(0, Math.min(PAGE_SIZE, records.size()));
        boolean nextPage;
        int pageNumber = page;
        String msg = message;
        if (rows.size() == PAGE_SIZE) {
            pageNumber++;
            nextPage = records.size() > PAGE_SIZE;
            if (!nextPage) {
                msg = MSG_REACHED_BOTTOM;
            }
        } else {
            msg = MSG_REACHED_BOTTOM;
            nextPage = false;
            if (!rows.isEmpty()) {
                pageNumber++;
            }
        }
        return info(page(rows, pageNumber, nextPage), msg);
    }

    /** PROCESS-PAGE-BACKWARD: STARTBR at the first key, READPREV it once, then up to ten READPREVs. */
    private ScreenResponse<KeysetPage<TransactionListRow>> backward(String key, int page) {
        List<Transaction> start = store.readFrom(key, 1);
        if (start.isEmpty()) {
            return info(null, MSG_AT_TOP);
        }
        List<Transaction> prev = store.readBefore(start.get(0).getTranId(), PAGE_SIZE + 1);
        List<Transaction> rows = new ArrayList<>(prev.subList(0, Math.min(PAGE_SIZE, prev.size())));
        Collections.reverse(rows);
        int pageNumber = page;
        String msg = null;
        if (rows.size() < PAGE_SIZE) {
            msg = MSG_REACHED_TOP;
        } else if (prev.size() > PAGE_SIZE && page > 1) {
            pageNumber = page - 1;
        } else {
            if (prev.size() <= PAGE_SIZE) {
                msg = MSG_REACHED_TOP;
            }
            pageNumber = 1;
        }
        return info(page(rows, pageNumber, true), msg);
    }

    private static KeysetPage<TransactionListRow> page(List<Transaction> rows, int pageNumber, boolean nextPage) {
        List<TransactionListRow> items = rows.stream()
                .map(t -> new TransactionListRow(t.getTranId(), TranFormat.mmddyy(t.getOrigTs()),
                        TranFormat.fit(t.getDescription(), 26), TranFormat.amount(t.getAmount())))
                .toList();
        String first = items.isEmpty() ? null : items.get(0).tranId();
        String last = items.isEmpty() ? null : items.get(items.size() - 1).tranId();
        return new KeysetPage<>(items, pageNumber, first, last, pageNumber > 1, nextPage);
    }

    private static ScreenResponse<KeysetPage<TransactionListRow>> info(KeysetPage<TransactionListRow> data,
            String message) {
        return new ScreenResponse<>(data, message, message == null ? null : MessageType.INFO, FIELD, null);
    }

    private static <T> T lookup(java.util.function.Supplier<T> browse) {
        try {
            return browse.get();
        } catch (DataAccessException e) {
            // STARTBR / READNEXT / READPREV: WHEN OTHER
            throw CardDemoException.failure(MSG_UNABLE_LOOKUP, FIELD);
        }
    }
}
