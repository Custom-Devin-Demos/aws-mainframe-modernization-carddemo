package com.carddemo.online.user;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.KeysetPage;
import com.carddemo.online.common.ScreenResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COUSR00C: browse USRSEC ten users at a time. */
@Service
public class UserListService {

    static final int PAGE_SIZE = 10;

    static final String MSG_AT_TOP = "You are at the top of the page...";
    static final String MSG_REACHED_BOTTOM = "You have reached the bottom of the page...";
    static final String MSG_REACHED_TOP = "You have reached the top of the page...";
    static final String MSG_ALREADY_TOP = "You are already at the top of the page...";
    static final String MSG_ALREADY_BOTTOM = "You are already at the bottom of the page...";
    static final String MSG_LOOKUP_FAILED = "Unable to lookup User...";
    static final String MSG_INVALID_SELECTION = "Invalid selection. Valid values are U and D";

    /** The search field USRIDIN gets the cursor for every COUSR00C message. */
    static final String FIELD = "startUserId";

    private final UserSecurityStore store;

    public UserListService(UserSecurityStore store) {
        this.store = store;
    }

    /**
     * ENTER (PROCESS-ENTER-KEY): a 'U'/'D' selection XCTLs to COUSR02C/COUSR03C; otherwise the list restarts at
     * page 1 from the search user id (STARTBR GTEQ, or from the first key when blank).
     */
    @Transactional(readOnly = true)
    public ScreenResponse<KeysetPage<UserView>> enter(String startUserId, String selection, String selectedUserId) {
        if (!CobolText.isBlank(selection) && !CobolText.isBlank(selectedUserId)) {
            switch (selection.trim()) {
                case "U", "u" -> {
                    return new ScreenResponse<KeysetPage<UserView>>(null, null, null, null, "COUSR02C");
                }
                case "D", "d" -> {
                    return new ScreenResponse<KeysetPage<UserView>>(null, null, null, null, "COUSR03C");
                }
                default -> {
                    ScreenResponse<KeysetPage<UserView>> page = pageForward(startKey(startUserId), true, 0);
                    throw CardDemoException.validation(MSG_INVALID_SELECTION, FIELD).withData(page.data());
                }
            }
        }
        return pageForward(startKey(startUserId), true, 0);
    }

    /** PF8 (PROCESS-PF8-KEY): next ten users after {@code lastKey}. */
    @Transactional(readOnly = true)
    public ScreenResponse<KeysetPage<UserView>> next(String lastKey, int pageNum) {
        try {
            if (!store.existsAfter(CobolText.rtrim(lastKey))) {
                return info(null, MSG_ALREADY_BOTTOM);
            }
        } catch (DataAccessException e) {
            throw lookupFailed();
        }
        return pageForward(CobolText.rtrim(lastKey), false, pageNum);
    }

    /** PF7 (PROCESS-PF7-KEY): the ten users before {@code firstKey}; only allowed past page 1. */
    @Transactional(readOnly = true)
    public ScreenResponse<KeysetPage<UserView>> previous(String firstKey, int pageNum) {
        if (pageNum <= 1) {
            return info(null, MSG_ALREADY_TOP);
        }
        return pageBackward(CobolText.rtrim(firstKey), pageNum);
    }

    private static String startKey(String startUserId) {
        // MOVE LOW-VALUES TO SEC-USR-ID when the search field is blank
        return CobolText.isBlank(startUserId) ? "" : CobolText.rtrim(startUserId);
    }

    /** PROCESS-PAGE-FORWARD. */
    private ScreenResponse<KeysetPage<UserView>> pageForward(String key, boolean inclusive, int pageNum) {
        List<UserRecord> rows;
        try {
            rows = inclusive ? store.browseFrom(key, PAGE_SIZE + 1) : store.browseAfter(key, PAGE_SIZE + 1);
        } catch (DataAccessException e) {
            throw lookupFailed();
        }
        if (rows.isEmpty() && inclusive) {
            // STARTBR-USER-SEC-FILE: NOTFND
            return info(page(List.of(), pageNum, false), MSG_AT_TOP);
        }
        String message = null;
        boolean nextPage;
        int newPage = pageNum;
        if (rows.size() >= PAGE_SIZE) {
            newPage++;
            nextPage = rows.size() > PAGE_SIZE;
            if (!nextPage) {
                // READNEXT-USER-SEC-FILE: ENDFILE on the look-ahead read
                message = MSG_REACHED_BOTTOM;
            }
        } else {
            // READNEXT-USER-SEC-FILE: ENDFILE while filling the screen
            message = MSG_REACHED_BOTTOM;
            nextPage = false;
            if (!rows.isEmpty()) {
                newPage++;
            }
        }
        List<UserRecord> shown = rows.subList(0, Math.min(rows.size(), PAGE_SIZE));
        return info(page(shown, newPage, nextPage), message);
    }

    /** PROCESS-PAGE-BACKWARD. */
    private ScreenResponse<KeysetPage<UserView>> pageBackward(String key, int pageNum) {
        List<UserRecord> rows;
        try {
            rows = new ArrayList<>(store.browseBefore(key, PAGE_SIZE + 1));
        } catch (DataAccessException e) {
            throw lookupFailed();
        }
        String message = null;
        int newPage = pageNum;
        if (rows.size() < PAGE_SIZE) {
            // READPREV-USER-SEC-FILE: ENDFILE while filling the screen; the page number is left unchanged
            message = MSG_REACHED_TOP;
        } else if (rows.size() == PAGE_SIZE) {
            // READPREV-USER-SEC-FILE: ENDFILE on the look-ahead read
            message = MSG_REACHED_TOP;
            newPage = 1;
        } else {
            newPage = pageNum > 1 ? pageNum - 1 : 1;
        }
        List<UserRecord> shown = new ArrayList<>(rows.subList(0, Math.min(rows.size(), PAGE_SIZE)));
        Collections.reverse(shown);
        return info(page(shown, newPage, true), message);
    }

    private static KeysetPage<UserView> page(List<UserRecord> rows, int pageNum, boolean nextPage) {
        List<UserView> items = rows.stream().map(UserRecord::toView).toList();
        return new KeysetPage<>(items, pageNum, items.isEmpty() ? null : items.get(0).userId(),
                items.isEmpty() ? null : items.get(items.size() - 1).userId(), pageNum > 1, nextPage);
    }

    private static ScreenResponse<KeysetPage<UserView>> info(KeysetPage<UserView> page, String message) {
        ScreenResponse<KeysetPage<UserView>> res = message == null ? ScreenResponse.of(page)
                : ScreenResponse.of(page, message);
        return res.withField(FIELD);
    }

    private static CardDemoException lookupFailed() {
        return CardDemoException.failure(MSG_LOOKUP_FAILED, FIELD);
    }
}
