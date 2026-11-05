package com.carddemo.online.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class CardDetailIT extends AbstractIntegrationTest {

    private static final String CARD = "0500024453765740";
    private static final String ACCT = "00000000050";

    @Test
    void showsCardDetails() throws Exception {
        // 9000-READ-DATA / 9100-GETCARD-BYACCTCARD: FOUND-CARDS-FOR-ACCOUNT
        mvc.perform(as(USER, get("/api/cards/{c}", CARD).param("acctId", ACCT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardNum").value(CARD))
                .andExpect(jsonPath("$.data.acctId").value(ACCT))
                .andExpect(jsonPath("$.data.embossedName").value("Aniya Von"))
                .andExpect(jsonPath("$.data.activeStatus").value("Y"))
                .andExpect(jsonPath("$.data.expiryMonth").value("03"))
                .andExpect(jsonPath("$.data.expiryYear").value("2023"))
                .andExpect(jsonPath("$.data.infoMessage").value("   Displaying requested details"))
                .andExpect(jsonPath("$.messageType").value("INFO"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void forUpdateUpperCasesName() throws Exception {
        // COCRDUPC 9000-READ-DATA: FUNCTION UPPER-CASE(CARD-EMBOSSED-NAME)
        mvc.perform(as(ADMIN, get("/api/cards/{c}", CARD).param("acctId", ACCT).param("forUpdate", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.embossedName").value("ANIYA VON"))
                .andExpect(jsonPath("$.data.expiryDay").value("09"))
                .andExpect(jsonPath("$.data.infoMessage").value("Details of selected card shown above"));
    }

    @Test
    void accountNotProvided() throws Exception {
        // 2210-EDIT-ACCOUNT: CC-ACCT-ID = SPACES/LOW-VALUES ('*' is the blank marker)
        mvc.perform(as(USER, get("/api/cards/{c}", CARD).param("acctId", "*")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account number not provided"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.data.infoMessage").value("Please enter Account and Card Number"));
    }

    @Test
    void accountNotNumeric() throws Exception {
        // 2210-EDIT-ACCOUNT: NOT NUMERIC
        mvc.perform(as(USER, get("/api/cards/{c}", CARD).param("acctId", "ABCDEFGHIJK")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void cardNotProvided() throws Exception {
        // 2220-EDIT-CARD: CC-CARD-NUM = SPACES/LOW-VALUES
        mvc.perform(as(USER, get("/api/cards/{c}", " ").param("acctId", ACCT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card number not provided"))
                .andExpect(jsonPath("$.field").value("cardNum"));
    }

    @Test
    void cardNotNumeric() throws Exception {
        // 2220-EDIT-CARD: NOT NUMERIC
        mvc.perform(as(USER, get("/api/cards/{c}", "1234").param("acctId", ACCT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"))
                .andExpect(jsonPath("$.field").value("cardNum"));
    }

    @Test
    void accountErrorReportedBeforeCardError() throws Exception {
        // 2200-EDIT-MAP-INPUTS: first WS-RETURN-MSG wins
        mvc.perform(as(USER, get("/api/cards/{c}", "1234").param("acctId", "12")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void noInputReceived() throws Exception {
        // 2200-EDIT-MAP-INPUTS: FLG-ACCTFILTER-BLANK AND FLG-CARDFILTER-BLANK
        mvc.perform(as(USER, get("/api/cards/{c}", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No input received"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void cardNotFound() throws Exception {
        // 9100-GETCARD-BYACCTCARD: DFHRESP(NOTFND)
        mvc.perform(as(USER, get("/api/cards/{c}", "1111222233334444").param("acctId", ACCT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Did not find cards for this search condition"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.data.cardNum").value("1111222233334444"));
    }
}
