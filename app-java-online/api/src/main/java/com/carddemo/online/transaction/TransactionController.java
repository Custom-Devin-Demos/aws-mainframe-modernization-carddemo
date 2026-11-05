package com.carddemo.online.transaction;

import com.carddemo.online.common.KeysetPage;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.session.SessionUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** COTRN00C (list), COTRN01C (view) and COTRN02C (add). */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionListService list;
    private final TransactionViewService view;
    private final TransactionAddService add;

    public TransactionController(TransactionListService list, TransactionViewService view,
            TransactionAddService add) {
        this.list = list;
        this.view = view;
        this.add = add;
    }

    /**
     * COTRN00C. ENTER: {@code startTranId} plus the first non-blank row selection ({@code selection} and the
     * row's {@code selectedTranId}); PF8: {@code after=<lastKey>}; PF7: {@code before=<firstKey>}.
     * {@code page} is the current page number (CDEMO-CT00-PAGE-NUM).
     */
    @GetMapping
    public ScreenResponse<KeysetPage<TransactionListRow>> list(SessionUser user,
            @RequestParam(required = false) String startTranId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String selection,
            @RequestParam(required = false) String selectedTranId) {
        if (after != null) {
            return list.pageForward(after, page);
        }
        if (before != null) {
            return list.pageBackward(before, page);
        }
        return list.enter(startTranId, selection, selectedTranId);
    }

    /** COTRN01C ENTER. */
    @GetMapping("/{tranId}")
    public ScreenResponse<TransactionView> view(SessionUser user, @PathVariable String tranId) {
        return view.view(tranId);
    }

    /** COTRN02C ENTER. */
    @PostMapping
    public ScreenResponse<TransactionAdded> add(SessionUser user, @RequestBody TransactionForm form) {
        return add.add(form);
    }

    /** COTRN02C PF5 (copy last transaction). */
    @PostMapping("/copy-last")
    public ScreenResponse<TransactionAdded> copyLast(SessionUser user, @RequestBody TransactionForm form) {
        return add.copyLast(form);
    }
}
