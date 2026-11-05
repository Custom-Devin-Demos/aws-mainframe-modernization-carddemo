package com.carddemo.online.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** COTRN00C - List Transactions (300 sample TRANSACT records, 10 per page). */
class TransactionListIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    List<String> ids;

    @BeforeEach
    void loadKeys() {
        ids = jdbc.queryForList("SELECT tran_id FROM transaction ORDER BY tran_id", String.class);
    }

    private ResultActions list(String query) throws Exception {
        return mvc.perform(as(USER, get("/api/transactions" + query)));
    }

    private void expectRows(ResultActions r, int from, int count) throws Exception {
        r.andExpect(jsonPath("$.data.items.length()").value(count));
        for (int i = 0; i < count; i++) {
            r.andExpect(jsonPath("$.data.items[" + i + "].tranId").value(ids.get(from + i)));
        }
    }

    @Test
    void requiresSession() throws Exception {
        // MAIN-PARA: EIBCALEN = 0 -> COSGN00C
        mvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }

    @Test
    void firstEntryListsFirstTenTransactions() throws Exception {
        // PROCESS-ENTER-KEY with blank TRNIDINI -> STARTBR LOW-VALUES, PROCESS-PAGE-FORWARD
        ResultActions r = list("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.field").value("startTranId"))
                .andExpect(jsonPath("$.data.pageNumber").value(1))
                .andExpect(jsonPath("$.data.firstKey").value(ids.get(0)))
                .andExpect(jsonPath("$.data.lastKey").value(ids.get(9)))
                .andExpect(jsonPath("$.data.hasPrevPage").value(false))
                .andExpect(jsonPath("$.data.hasNextPage").value(true))
                // POPULATE-TRAN-DATA: MM/DD/YY date, 26-char description, +99999999.99 amount
                .andExpect(jsonPath("$.data.items[0].tranId").value("0000000000683580"))
                .andExpect(jsonPath("$.data.items[0].date").value("06/10/22"))
                .andExpect(jsonPath("$.data.items[0].description").value("Purchase at Abshire-Lowe"))
                .andExpect(jsonPath("$.data.items[0].amount").value("+00000504.77"));
        expectRows(r, 0, 10);
    }

    @Test
    void descriptionIsTruncatedToTheMapField() throws Exception {
        list("").andExpect(jsonPath("$.data.items[1].description").value("Return item at Nitzsche, N"));
    }

    @Test
    void enterStartsBrowseAtSearchTranId() throws Exception {
        // PROCESS-ENTER-KEY: TRNIDINI numeric -> STARTBR GTEQ
        expectRows(list("?startTranId=" + ids.get(5)).andExpect(jsonPath("$.data.pageNumber").value(1)), 5, 10);
    }

    @Test
    void searchTranIdMustBeNumeric() throws Exception {
        // PROCESS-ENTER-KEY: TRNIDINI NOT NUMERIC
        list("?startTranId=12AB")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tran ID must be Numeric ..."))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value("startTranId"));
    }

    @Test
    void searchBeyondLastKeyIsNotFound() throws Exception {
        // STARTBR-TRANSACT-FILE: NOTFND
        list("?startTranId=9999999999999999")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You are at the top of the page..."))
                .andExpect(jsonPath("$.messageType").value("INFO"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void partialLastPageReachesBottom() throws Exception {
        // READNEXT-TRANSACT-FILE: ENDFILE inside the 10-row loop
        ResultActions r = list("?startTranId=" + ids.get(295))
                .andExpect(jsonPath("$.message").value("You have reached the bottom of the page..."))
                .andExpect(jsonPath("$.data.pageNumber").value(1))
                .andExpect(jsonPath("$.data.hasNextPage").value(false));
        expectRows(r, 295, 5);
    }

    @Test
    void fullLastPageReachesBottomOnLookahead() throws Exception {
        // PROCESS-PAGE-FORWARD: 11th READNEXT hits ENDFILE -> NEXT-PAGE-NO
        ResultActions r = list("?startTranId=" + ids.get(290))
                .andExpect(jsonPath("$.message").value("You have reached the bottom of the page..."))
                .andExpect(jsonPath("$.data.hasNextPage").value(false));
        expectRows(r, 290, 10);
    }

    @Test
    void selectingWithSTransfersToView() throws Exception {
        // PROCESS-ENTER-KEY: SEL = 'S' -> XCTL COTRN01C
        list("?selection=S&selectedTranId=" + ids.get(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextProgram").value("COTRN01C"))
                .andExpect(jsonPath("$.data").doesNotExist());
        list("?selection=s&selectedTranId=" + ids.get(3)).andExpect(jsonPath("$.nextProgram").value("COTRN01C"));
    }

    @Test
    void invalidSelectionStillListsRows() throws Exception {
        // PROCESS-ENTER-KEY: WHEN OTHER -> 'Invalid selection. Valid value is S', browse continues
        ResultActions r = list("?selection=X&selectedTranId=" + ids.get(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invalid selection. Valid value is S"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
        expectRows(r, 0, 10);
    }

    @Test
    void selectionWithoutTranIdIsIgnored() throws Exception {
        // PROCESS-ENTER-KEY: CDEMO-CT00-TRN-SELECTED = SPACES
        list("?selection=S&selectedTranId=").andExpect(jsonPath("$.nextProgram").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void pf8PagesForward() throws Exception {
        // PROCESS-PF8-KEY -> PROCESS-PAGE-FORWARD (skip the last key, read 10)
        ResultActions r = list("?after=" + ids.get(9) + "&page=1")
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.hasPrevPage").value(true))
                .andExpect(jsonPath("$.data.hasNextPage").value(true));
        expectRows(r, 10, 10);
    }

    @Test
    void pf8ToFinalPageReachesBottom() throws Exception {
        ResultActions r = list("?after=" + ids.get(289) + "&page=29")
                .andExpect(jsonPath("$.message").value("You have reached the bottom of the page..."))
                .andExpect(jsonPath("$.data.pageNumber").value(30))
                .andExpect(jsonPath("$.data.hasNextPage").value(false));
        expectRows(r, 290, 10);
    }

    @Test
    void pf8OnLastPageIsAlreadyAtBottom() throws Exception {
        // PROCESS-PF8-KEY: NEXT-PAGE-NO
        list("?after=" + ids.get(299) + "&page=30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You are already at the bottom of the page..."))
                .andExpect(jsonPath("$.data").doesNotExist());
        list("?after=&page=1").andExpect(jsonPath("$.message").value("You are already at the bottom of the page..."));
    }

    @Test
    void pf7OnFirstPageIsAlreadyAtTop() throws Exception {
        // PROCESS-PF7-KEY: CDEMO-CT00-PAGE-NUM <= 1
        list("?before=" + ids.get(0) + "&page=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You are already at the top of the page..."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void pf7PagesBackward() throws Exception {
        // PROCESS-PAGE-BACKWARD: READPREV the first key, then 10 READPREVs, lookahead found -> page - 1
        ResultActions r = list("?before=" + ids.get(20) + "&page=3")
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.hasNextPage").value(true));
        expectRows(r, 10, 10);
    }

    @Test
    void pf7ToFirstPageReachesTop() throws Exception {
        // PROCESS-PAGE-BACKWARD: lookahead READPREV hits ENDFILE -> page 1
        ResultActions r = list("?before=" + ids.get(10) + "&page=2")
                .andExpect(jsonPath("$.message").value("You have reached the top of the page..."))
                .andExpect(jsonPath("$.data.pageNumber").value(1))
                .andExpect(jsonPath("$.data.hasPrevPage").value(false));
        expectRows(r, 0, 10);
    }

    @Test
    void pf7WithFewerThanTenEarlierRowsKeepsPageNumber() throws Exception {
        // PROCESS-PAGE-BACKWARD: ENDFILE inside the loop leaves CDEMO-CT00-PAGE-NUM unchanged
        ResultActions r = list("?before=" + ids.get(5) + "&page=2")
                .andExpect(jsonPath("$.message").value("You have reached the top of the page..."))
                .andExpect(jsonPath("$.data.pageNumber").value(2));
        expectRows(r, 0, 5);
    }

    @Test
    void pf7FromKeyBeyondEndIsNotFound() throws Exception {
        // STARTBR-TRANSACT-FILE: NOTFND
        list("?before=9999999999999999&page=2")
                .andExpect(jsonPath("$.message").value("You are at the top of the page..."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
