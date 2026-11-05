package com.carddemo.online.card;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class CardListIT extends AbstractIntegrationTest {

    private static final String FIRST_CARD = "0500024453765740";
    private static final String PAGE1_LAST = "1142167692878931";
    private static final String PAGE2_FIRST = "1561409106491600";
    private static final String LAST_CARD = "9805583408996588";

    @Test
    void unauthenticatedGoesToSignOn() throws Exception {
        mvc.perform(get("/api/cards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }

    @Test
    void firstPageShowsSevenCardsInKeyOrder() throws Exception {
        // 9000-READ-FORWARD: 7 rows + look-ahead record found -> next page exists
        mvc.perform(as(USER, get("/api/cards")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.items", hasSize(7)))
                .andExpect(jsonPath("$.data.page.items[0].cardNum").value(FIRST_CARD))
                .andExpect(jsonPath("$.data.page.items[0].acctId").value("00000000050"))
                .andExpect(jsonPath("$.data.page.items[0].activeStatus").value("Y"))
                .andExpect(jsonPath("$.data.page.firstKey").value(FIRST_CARD))
                .andExpect(jsonPath("$.data.page.lastKey").value(PAGE1_LAST))
                .andExpect(jsonPath("$.data.page.pageNumber").value(1))
                .andExpect(jsonPath("$.data.page.hasPrevPage").value(false))
                .andExpect(jsonPath("$.data.page.hasNextPage").value(true))
                .andExpect(jsonPath("$.data.infoMessage").value("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD"))
                .andExpect(jsonPath("$.message").value("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD"))
                .andExpect(jsonPath("$.messageType").value("INFO"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void adminSeesTheSameListAsRegularUser() throws Exception {
        // COCRDLIC applies no user-type filter: only the screen filters restrict the browse
        mvc.perform(as(ADMIN, get("/api/cards")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.items", hasSize(7)))
                .andExpect(jsonPath("$.data.page.firstKey").value(FIRST_CARD))
                .andExpect(jsonPath("$.data.page.lastKey").value(PAGE1_LAST));
    }

    @Test
    void pf8ReadsNextPage() throws Exception {
        // PF8 AND CA-NEXT-PAGE-EXISTS -> 9000-READ-FORWARD from WS-CA-LAST-CARD-NUM
        mvc.perform(as(USER, get("/api/cards").param("after", PAGE1_LAST).param("page", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.items[0].cardNum").value(PAGE2_FIRST))
                .andExpect(jsonPath("$.data.page.pageNumber").value(2))
                .andExpect(jsonPath("$.data.page.hasPrevPage").value(true))
                .andExpect(jsonPath("$.data.page.hasNextPage").value(true));
    }

    @Test
    void pf7ReadsPreviousPage() throws Exception {
        // PF7 AND NOT CA-FIRST-PAGE -> 9100-READ-BACKWARDS from WS-CA-FIRST-CARD-NUM
        mvc.perform(as(USER, get("/api/cards").param("before", PAGE2_FIRST).param("page", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.items", hasSize(7)))
                .andExpect(jsonPath("$.data.page.firstKey").value(FIRST_CARD))
                .andExpect(jsonPath("$.data.page.lastKey").value(PAGE1_LAST))
                .andExpect(jsonPath("$.data.page.pageNumber").value(1))
                .andExpect(jsonPath("$.data.page.hasNextPage").value(true))
                .andExpect(jsonPath("$.data.infoMessage").value("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD"));
    }

    @Test
    void pf7OnFirstPage() throws Exception {
        // 1400-SETUP-MESSAGE: PF7 AND CA-FIRST-PAGE
        mvc.perform(as(USER, get("/api/cards").param("before", FIRST_CARD).param("page", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("NO PREVIOUS PAGES TO DISPLAY"))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.data.infoMessage").doesNotExist())
                .andExpect(jsonPath("$.data.page.firstKey").value(FIRST_CARD))
                .andExpect(jsonPath("$.data.page.pageNumber").value(1));
    }

    @Test
    void lastPageAndPf8BeyondIt() throws Exception {
        // 9000-READ-FORWARD: ENDFILE before 7 rows -> 'NO MORE RECORDS TO SHOW'
        mvc.perform(as(USER, get("/api/cards").param("after", "9680294154603697").param("page", "7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.items", hasSize(1)))
                .andExpect(jsonPath("$.data.page.firstKey").value(LAST_CARD))
                .andExpect(jsonPath("$.data.page.pageNumber").value(8))
                .andExpect(jsonPath("$.data.page.hasNextPage").value(false))
                .andExpect(jsonPath("$.message").value("NO MORE RECORDS TO SHOW"))
                .andExpect(jsonPath("$.data.infoMessage").value("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD"));
        // 1400-SETUP-MESSAGE: PF8 AND CA-NEXT-PAGE-NOT-EXISTS -> same page re-shown
        mvc.perform(as(USER, get("/api/cards").param("after", LAST_CARD).param("start", LAST_CARD)
                        .param("page", "8")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("NO MORE PAGES TO DISPLAY"))
                .andExpect(jsonPath("$.data.page.firstKey").value(LAST_CARD))
                .andExpect(jsonPath("$.data.page.pageNumber").value(8))
                .andExpect(jsonPath("$.data.infoMessage").doesNotExist());
    }

    @Test
    void accountFilter() throws Exception {
        // 9500-FILTER-RECORDS: CARD-ACCT-ID = CC-ACCT-ID
        mvc.perform(as(USER, get("/api/cards").param("acctId", "00000000050")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.items", hasSize(1)))
                .andExpect(jsonPath("$.data.page.items[0].cardNum").value(FIRST_CARD))
                .andExpect(jsonPath("$.message").value("NO MORE RECORDS TO SHOW"));
    }

    @Test
    void cardFilter() throws Exception {
        // 9500-FILTER-RECORDS: CARD-NUM = CC-CARD-NUM
        mvc.perform(as(USER, get("/api/cards").param("cardNum", PAGE2_FIRST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.items", hasSize(1)))
                .andExpect(jsonPath("$.data.page.items[0].acctId").value("00000000035"));
    }

    @Test
    void zeroFiltersAreIgnored() throws Exception {
        // 2210-EDIT-ACCOUNT / 2220-EDIT-CARD: SPACES/LOW-VALUES/ZEROS -> filter blank
        mvc.perform(as(USER, get("/api/cards").param("acctId", "00000000000").param("cardNum", "0000000000000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.items", hasSize(7)));
    }

    @Test
    void noRecordsFound() throws Exception {
        // 9000-READ-FORWARD: WS-CA-SCREEN-NUM = 1 AND WS-SCRN-COUNTER = 0
        mvc.perform(as(USER, get("/api/cards").param("acctId", "99999999999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.items", hasSize(0)))
                .andExpect(jsonPath("$.message").value("NO RECORDS FOUND FOR THIS SEARCH CONDITION."))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.data.infoMessage").doesNotExist());
    }

    @Test
    void accountFilterNotNumeric() throws Exception {
        // 2210-EDIT-ACCOUNT: CC-ACCT-ID IS NOT NUMERIC
        mvc.perform(as(USER, get("/api/cards").param("acctId", "12345ABC")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist())
                .andExpect(jsonPath("$.data.page.items", hasSize(0)));
    }

    @Test
    void accountFilterShort() throws Exception {
        // 2210-EDIT-ACCOUNT: a partly filled PIC X(11) is NOT NUMERIC
        mvc.perform(as(USER, get("/api/cards").param("acctId", "50")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void cardFilterNotNumeric() throws Exception {
        // 2220-EDIT-CARD: CC-CARD-NUM IS NOT NUMERIC
        mvc.perform(as(USER, get("/api/cards").param("cardNum", "05000244537657XX")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"))
                .andExpect(jsonPath("$.field").value("cardNum"));
    }

    @Test
    void accountErrorWinsOverCardError() throws Exception {
        // 2200-EDIT-INPUTS: message set only IF WS-ERROR-MSG-OFF; cursor on first flagged field
        mvc.perform(as(USER, get("/api/cards").param("acctId", "X").param("cardNum", "Y")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void selectSGoesToDetail() throws Exception {
        // 0000-MAIN: CCARD-SELECT-ACTION 'S' -> XCTL COCRDSLC
        mvc.perform(as(USER, get("/api/cards").param("start", FIRST_CARD).param("selections", ",,S,,,,")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextProgram").value("COCRDSLC"))
                .andExpect(jsonPath("$.data.selectedCardNum").value("0923877193247330"))
                .andExpect(jsonPath("$.data.selectedAcctId").value("00000000002"));
    }

    @Test
    void selectUGoesToUpdate() throws Exception {
        // 0000-MAIN: CCARD-SELECT-ACTION 'U' -> XCTL COCRDUPC
        mvc.perform(as(ADMIN, get("/api/cards").param("start", PAGE2_FIRST).param("page", "2")
                        .param("selections", "U,,,,,,")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextProgram").value("COCRDUPC"))
                .andExpect(jsonPath("$.data.selectedCardNum").value(PAGE2_FIRST))
                .andExpect(jsonPath("$.data.selectedAcctId").value("00000000035"));
    }

    @Test
    void moreThanOneSelection() throws Exception {
        // 2250-EDIT-ARRAY: WS-EDIT-SELECT-COUNTER > 1
        mvc.perform(as(USER, get("/api/cards").param("start", FIRST_CARD).param("selections", ",S,,U,,,")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE"))
                .andExpect(jsonPath("$.field").value("select2"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist())
                .andExpect(jsonPath("$.data.errorRows", hasSize(2)))
                .andExpect(jsonPath("$.data.page.firstKey").value(FIRST_CARD));
    }

    @Test
    void invalidActionCode() throws Exception {
        // 2250-EDIT-ARRAY: select code not S/U and not blank
        mvc.perform(as(USER, get("/api/cards").param("start", FIRST_CARD).param("selections", ",,,,X,,")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID ACTION CODE"))
                .andExpect(jsonPath("$.field").value("select5"))
                .andExpect(jsonPath("$.data.errorRows[0]").value(5));
    }

    @Test
    void validSelectionWithAnotherInvalidCode() throws Exception {
        // 2250-EDIT-ARRAY: one S plus an invalid code -> INVALID ACTION CODE, no XCTL
        mvc.perform(as(USER, get("/api/cards").param("start", FIRST_CARD).param("selections", "S,,,,,,s")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID ACTION CODE"))
                .andExpect(jsonPath("$.field").value("select7"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void multipleSelectionWinsOverInvalidCode() throws Exception {
        // 2250-EDIT-ARRAY: the >1 message is set first; invalid rows are still flagged
        mvc.perform(as(USER, get("/api/cards").param("start", FIRST_CARD).param("selections", "X,S,U,,,,")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE"))
                .andExpect(jsonPath("$.field").value("select1"))
                .andExpect(jsonPath("$.data.errorRows", hasSize(3)));
    }

    @Test
    void selectionIgnoredWhenFilterInvalid() throws Exception {
        // 2200-EDIT-INPUTS: GO TO exit on filter error before 2250-EDIT-ARRAY
        mvc.perform(as(USER, get("/api/cards").param("acctId", "ABC").param("selections", "S,,,,,,")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }
}
