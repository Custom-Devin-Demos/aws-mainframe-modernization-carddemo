package com.carddemo.online.card;

import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.session.SessionUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * COCRDLIC (list), COCRDSLC (view) and COCRDUPC (update). Admin and regular users are handled the
 * same way: the COBOL sets no user-type specific filter (only the screen account/card filters apply).
 */
@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardListService listService;
    private final CardDetailService detailService;
    private final CardUpdateService updateService;

    public CardController(CardListService listService, CardDetailService detailService,
            CardUpdateService updateService) {
        this.listService = listService;
        this.detailService = detailService;
        this.updateService = updateService;
    }

    @GetMapping
    public ScreenResponse<CardListView> list(SessionUser user,
            @RequestParam(required = false) String acctId,
            @RequestParam(required = false) String cardNum,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String selections) {
        return listService.list(new CardListService.ListRequest(acctId, cardNum, start, after, before, page,
                selections));
    }

    /** A blank card number is sent as an encoded space ({@code /api/cards/%20}). */
    @GetMapping("/{cardNum}")
    public ScreenResponse<CardDetailView> view(SessionUser user, @PathVariable String cardNum,
            @RequestParam(required = false) String acctId,
            @RequestParam(defaultValue = "false") boolean forUpdate) {
        return detailService.find(acctId, cardNum, forUpdate);
    }

    @PutMapping("/{cardNum}")
    public ScreenResponse<CardUpdateView> update(SessionUser user, @PathVariable String cardNum,
            @RequestBody CardUpdateRequest request) {
        return updateService.update(cardNum, request);
    }
}
