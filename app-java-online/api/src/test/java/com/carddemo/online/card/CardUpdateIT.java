package com.carddemo.online.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class CardUpdateIT extends AbstractIntegrationTest {

    private static final String CARD = "0500024453765740";
    private static final String ACCT = "00000000050";
    private static final CardUpdateRequest.Original ORIGINAL =
            new CardUpdateRequest.Original("ANIYA VON", "Y", "2023", "03", "09");

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        resetSampleData();
    }

    private ResultActions send(String card, CardUpdateRequest body) throws Exception {
        return mvc.perform(as(USER, put("/api/cards/{c}", card)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))));
    }

    private ResultActions change(String name, String status, String month, String year, boolean confirm)
            throws Exception {
        return send(CARD, new CardUpdateRequest(ACCT, name, status, month, year, ORIGINAL, confirm));
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT * FROM card WHERE card_num = ?", CARD);
    }

    @Test
    void searchWithoutOriginalFetchesDetails() throws Exception {
        // 2000-DECIDE-ACTION: CCUP-DETAILS-NOT-FETCHED -> 9000-READ-DATA
        send(CARD, new CardUpdateRequest(ACCT, null, null, null, null, null, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("SHOW_DETAILS"))
                .andExpect(jsonPath("$.data.card.embossedName").value("ANIYA VON"))
                .andExpect(jsonPath("$.data.original.expiryDay").value("09"))
                .andExpect(jsonPath("$.message").value("Details of selected card shown above"));
    }

    @Test
    void searchCardNotFound() throws Exception {
        // 9100-GETCARD-BYACCTCARD: NOTFND
        send("1111222233334444", new CardUpdateRequest(ACCT, null, null, null, null, null, false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Did not find cards for this search condition"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.data.state").value("DETAILS_NOT_FETCHED"));
    }

    @Test
    void searchNoInput() throws Exception {
        // 1200-EDIT-MAP-INPUTS: both search keys blank
        send(" ", new CardUpdateRequest("", null, null, null, null, null, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No input received"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.data.card.infoMessage").value("Please enter Account and Card Number"));
    }

    @Test
    void accountNotProvided() throws Exception {
        // 1210-EDIT-ACCOUNT: blank
        send(CARD, new CardUpdateRequest(" ", "NEW NAME", "Y", "03", "2023", ORIGINAL, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account number not provided"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void accountNotNumeric() throws Exception {
        // 1210-EDIT-ACCOUNT: NOT NUMERIC
        send(CARD, new CardUpdateRequest("0000000005X", "NEW NAME", "Y", "03", "2023", ORIGINAL, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void cardNotNumeric() throws Exception {
        // 1220-EDIT-CARD: NOT NUMERIC
        send("05000244537657AB", new CardUpdateRequest(ACCT, "NEW NAME", "Y", "03", "2023", ORIGINAL, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"))
                .andExpect(jsonPath("$.field").value("cardNum"));
    }

    @Test
    void noChangeDetected() throws Exception {
        // 1200-EDIT-MAP-INPUTS: UPPER-CASE(new) = UPPER-CASE(old)
        change("aniya von", "y", "3", "2023", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No change detected with respect to values fetched."))
                .andExpect(jsonPath("$.field").value("embossedName"))
                .andExpect(jsonPath("$.data.state").value("SHOW_DETAILS"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void nameNotProvided() throws Exception {
        // 1230-EDIT-NAME: SPACES/LOW-VALUES/ZEROS
        change("   ", "Y", "03", "2023", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card name not provided"))
                .andExpect(jsonPath("$.field").value("embossedName"))
                .andExpect(jsonPath("$.data.state").value("CHANGES_NOT_OK"))
                .andExpect(jsonPath("$.data.card.infoMessage").value("Update card details presented above."));
    }

    @Test
    void nameNotAlphabetic() throws Exception {
        // 1230-EDIT-NAME: INSPECT CONVERTING alphabets to spaces leaves non-spaces
        change("ANIYA VON 2", "Y", "03", "2023", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card name can only contain alphabets and spaces"))
                .andExpect(jsonPath("$.field").value("embossedName"));
    }

    @Test
    void statusBlank() throws Exception {
        // 1240-EDIT-CARDSTATUS: SPACES/LOW-VALUES/ZEROS
        change("ANIYA VON", " ", "03", "2023", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card Active Status must be Y or N"))
                .andExpect(jsonPath("$.field").value("activeStatus"));
    }

    @Test
    void statusNotYesNo() throws Exception {
        // 1240-EDIT-CARDSTATUS: NOT FLG-YES-NO-VALID
        change("ANIYA VON", "X", "03", "2023", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card Active Status must be Y or N"))
                .andExpect(jsonPath("$.field").value("activeStatus"));
    }

    @Test
    void monthBlank() throws Exception {
        // 1250-EDIT-EXPIRY-MON: SPACES/LOW-VALUES/ZEROS
        change("ANIYA VON", "Y", "00", "2023", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card expiry month must be between 1 and 12"))
                .andExpect(jsonPath("$.field").value("expiryMonth"));
    }

    @Test
    void monthOutOfRange() throws Exception {
        // 1250-EDIT-EXPIRY-MON: NOT VALID-MONTH
        change("ANIYA VON", "Y", "13", "2023", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card expiry month must be between 1 and 12"))
                .andExpect(jsonPath("$.field").value("expiryMonth"));
    }

    @Test
    void yearBlank() throws Exception {
        // 1260-EDIT-EXPIRY-YEAR: SPACES/LOW-VALUES/ZEROS
        change("ANIYA VON", "Y", "03", "", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid card expiry year"))
                .andExpect(jsonPath("$.field").value("expiryYear"));
    }

    @Test
    void yearOutOfRange() throws Exception {
        // 1260-EDIT-EXPIRY-YEAR: NOT VALID-YEAR (1950 THRU 2099)
        change("ANIYA VON", "Y", "03", "2100", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid card expiry year"))
                .andExpect(jsonPath("$.field").value("expiryYear"));
    }

    @Test
    void firstFieldErrorWins() throws Exception {
        // 1200-EDIT-MAP-INPUTS: name edit runs first; later edits do not overwrite WS-RETURN-MSG
        change("", "X", "13", "1900", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card name not provided"))
                .andExpect(jsonPath("$.field").value("embossedName"));
    }

    @Test
    void validChangesAskForConfirmation() throws Exception {
        // 2000-DECIDE-ACTION: CCUP-CHANGES-OK-NOT-CONFIRMED
        change("Aniya Smith", "N", "11", "2027", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changes validated.Press F5 to save"))
                .andExpect(jsonPath("$.data.state").value("CHANGES_OK_NOT_CONFIRMED"));
        assertThat(row().get("embossed_name")).isEqualTo("Aniya Von");
    }

    @Test
    void confirmCommitsChanges() throws Exception {
        // 9200-WRITE-PROCESSING: REWRITE OK -> CCUP-CHANGES-OKAYED-AND-DONE
        change("Aniya Smith", "N", "11", "2027", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changes committed to database"))
                .andExpect(jsonPath("$.messageType").value("SUCCESS"))
                .andExpect(jsonPath("$.data.state").value("CHANGES_OKAYED_AND_DONE"))
                .andExpect(jsonPath("$.data.original.embossedName").value("Aniya Smith"));
        Map<String, Object> r = row();
        assertThat(r.get("embossed_name")).isEqualTo("Aniya Smith");
        assertThat(r.get("active_status")).isEqualTo("N");
        assertThat(r.get("expiration_date").toString()).isEqualTo("2027-11-09");
        assertThat(r.get("cvv_cd")).isEqualTo(747);
        assertThat(r.get("acct_id")).isEqualTo(50L);
    }

    @Test
    void confirmClampsDayToMonthLength() throws Exception {
        jdbc.update("UPDATE card SET expiration_date = DATE '2023-03-31' WHERE card_num = ?", CARD);
        send(CARD, new CardUpdateRequest(ACCT, "ANIYA VON", "Y", "02", "2023",
                new CardUpdateRequest.Original("ANIYA VON", "Y", "2023", "03", "31"), true))
                .andExpect(status().isOk());
        assertThat(row().get("expiration_date").toString()).isEqualTo("2023-02-28");
    }

    @Test
    void changedBySomeoneElse() throws Exception {
        // 9300-CHECK-CHANGE-IN-REC: record differs from CCUP-OLD-DETAILS
        jdbc.update("UPDATE card SET embossed_name = 'Someone Else' WHERE card_num = ?", CARD);
        change("Aniya Smith", "Y", "03", "2023", true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Record changed by some one else. Please review"))
                .andExpect(jsonPath("$.field").value("embossedName"))
                .andExpect(jsonPath("$.data.state").value("SHOW_DETAILS"))
                .andExpect(jsonPath("$.data.card.embossedName").value("SOMEONE ELSE"))
                .andExpect(jsonPath("$.data.original.embossedName").value("SOMEONE ELSE"));
        assertThat(row().get("embossed_name")).isEqualTo("Someone Else");
    }

    @Test
    void couldNotLock() throws Exception {
        // 9200-WRITE-PROCESSING: READ UPDATE fails -> COULD-NOT-LOCK-FOR-UPDATE
        send("1111222233334444", new CardUpdateRequest(ACCT, "NEW NAME", "Y", "03", "2023", ORIGINAL, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Could not lock record for update"))
                .andExpect(jsonPath("$.data.state").value("CHANGES_FAILED"))
                .andExpect(jsonPath("$.data.card.infoMessage").value("Changes unsuccessful. Please try again"));
    }

    @Test
    void rewriteFails() throws Exception {
        // 9200-WRITE-PROCESSING: REWRITE not NORMAL -> LOCKED-BUT-UPDATE-FAILED (name longer than the column)
        change("A".repeat(60), "Y", "03", "2023", true)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Update of record failed"))
                .andExpect(jsonPath("$.data.state").value("CHANGES_FAILED"));
        assertThat(row().get("embossed_name")).isEqualTo("Aniya Von");
    }
}
