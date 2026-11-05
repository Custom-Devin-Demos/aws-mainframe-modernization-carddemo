package com.carddemo.online.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** PUT /api/accounts/{acctId}: COACTUPC 2000-DECIDE-ACTION / 9600-WRITE-PROCESSING flows. */
class AccountUpdateIT extends AbstractAccountIT {

    @AfterEach
    void reset() {
        resetSampleData();
    }

    @Test
    void unchangedValuesAreNoChange() throws Exception {
        // 1205-COMPARE-OLD-NEW: NO-CHANGES-DETECTED (cursor on ACSTTUS)
        putAccount(ACCT_1, unchangedRequest(ACCT_1), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No change detected with respect to values fetched."))
                .andExpect(jsonPath("$.field").value("acctStatus"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void caseOnlyTextChangesAreNoChange() throws Exception {
        // 1205-COMPARE-OLD-NEW compares FUNCTION UPPER-CASE(FUNCTION TRIM(...))
        Map<String, Object> req = unchangedRequest(ACCT_1);
        req.put("firstName", "IMMANUEL");
        req.put("city", "altenwerthshire");
        req.put("acctStatus", "y");
        req.put("creditLimit", "+2,020.00");
        putAccount(ACCT_1, req, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No change detected with respect to values fetched."));
    }

    @Test
    void validateOnlyAsksForF5AndDoesNotSave() throws Exception {
        // 2000-DECIDE-ACTION: ACUP-SHOW-DETAILS and edits OK -> ACUP-CHANGES-OK-NOT-CONFIRMED
        putAccount(ACCT_1, validRequest(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changes validated.Press F5 to save"))
                .andExpect(jsonPath("$.messageType").value("INFO"));
        assertThat(fetch(ACCT_1).get("ficoScore").asText()).isEqualTo("274");
    }

    @Test
    void saveRewritesAccountAndCustomer() throws Exception {
        // 9600-WRITE-PROCESSING: REWRITE ACCTDAT + CUSTDAT -> CONFIRM-UPDATE-SUCCESS
        Map<String, Object> req = validRequest();
        String oldVersion = (String) req.get("version");
        req.put("creditLimit", "$3,500.50");
        req.put("currBal", "-12.34");
        req.put("acctStatus", "N");
        req.put("expiryYear", "2030");
        req.put("lastName", "Smith");
        req.put("addrLine2", "Suite 9");
        req.put("phone2a", "");
        req.put("phone2b", "");
        req.put("phone2c", "");
        req.put("groupId", "GOLD");
        String body = putAccount(ACCT_1, req, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changes committed to database"))
                .andExpect(jsonPath("$.messageType").value("SUCCESS"))
                .andExpect(jsonPath("$.data.lastName").value("Smith"))
                .andReturn().getResponse().getContentAsString();
        JsonNode saved = mapper.readTree(body).get("data");
        assertThat(saved.get("version").asText()).isNotEqualTo(oldVersion);

        Map<String, Object> acct = jdbc.queryForMap("SELECT * FROM account WHERE acct_id = 1");
        assertThat(acct.get("credit_limit")).isEqualTo(new BigDecimal("3500.50"));
        assertThat(acct.get("curr_bal")).isEqualTo(new BigDecimal("-12.34"));
        assertThat(acct.get("active_status")).isEqualTo("N");
        assertThat(acct.get("expiration_date").toString()).isEqualTo("2030-05-20");
        assertThat(acct.get("group_id")).isEqualTo("GOLD");
        Map<String, Object> cust = jdbc.queryForMap("SELECT * FROM customer WHERE cust_id = 1");
        assertThat(cust.get("last_name")).isEqualTo("Smith");
        assertThat(cust.get("addr_line_2")).isEqualTo("Suite 9");
        assertThat(cust.get("addr_zip")).isEqualTo("27601");
        assertThat(cust.get("fico_credit_score")).isEqualTo(750);
        assertThat(cust.get("phone_num_1")).isEqualTo("(908)119-8310");
        assertThat(cust.get("phone_num_2")).isEqualTo("");
        assertThat(cust.get("ssn")).isEqualTo("020973888");
        assertThat(cust.get("dob").toString()).isEqualTo("1961-06-08");
    }

    @Test
    void recordChangedSinceFetchIsRejected() throws Exception {
        // 9700-CHECK-CHANGE-IN-REC: DATA-WAS-CHANGED-BEFORE-UPDATE, fresh values shown for review
        Map<String, Object> req = validRequest();
        jdbc.update("UPDATE account SET credit_limit = 9999.00 WHERE acct_id = 1");
        putAccount(ACCT_1, req, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Record changed by some one else. Please review"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.data.creditLimit").value(9999.00));
        assertThat(jdbc.queryForObject("SELECT fico_credit_score FROM customer WHERE cust_id = 1", Integer.class))
                .isEqualTo(274);
    }

    @Test
    void customerChangedSinceFetchIsRejected() throws Exception {
        // 9700-CHECK-CHANGE-IN-REC: customer part
        Map<String, Object> req = validRequest();
        jdbc.update("UPDATE customer SET last_name = 'Other' WHERE cust_id = 1");
        putAccount(ACCT_1, req, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Record changed by some one else. Please review"));
    }

    @Test
    void missingVersionCountsAsChanged() throws Exception {
        Map<String, Object> req = validRequest();
        req.remove("version");
        putAccount(ACCT_1, req, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Record changed by some one else. Please review"));
    }

    @Test
    void accountRecordCannotBeLocked() throws Exception {
        // 9600-WRITE-PROCESSING: READ ACCTDAT UPDATE not NORMAL -> COULD-NOT-LOCK-ACCT-FOR-UPDATE
        putAccount("99999999999", validRequest(), false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Could not lock account record for update"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void customerRecordCannotBeLocked() throws Exception {
        // 9600-WRITE-PROCESSING: READ CUSTDAT UPDATE not NORMAL -> COULD-NOT-LOCK-CUST-FOR-UPDATE
        insertUnchecked(
                "INSERT INTO account (acct_id, active_status) VALUES (99999999993, 'Y')",
                "INSERT INTO card_xref (card_num, cust_id, acct_id) VALUES ('9999999999999993', 999999993, 99999999993)");
        putAccount("99999999993", validRequest(), false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Could not lock customer record for update"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void failedCustomerRewriteRollsBackAccount() throws Exception {
        // 9600-WRITE-PROCESSING: REWRITE CUSTDAT not NORMAL -> LOCKED-BUT-UPDATE-FAILED + SYNCPOINT ROLLBACK
        jdbc.execute("CREATE OR REPLACE FUNCTION accounts_it_fail() RETURNS trigger AS "
                + "$$ BEGIN RAISE EXCEPTION 'rewrite failed'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER accounts_it_fail BEFORE UPDATE ON customer "
                + "FOR EACH ROW EXECUTE FUNCTION accounts_it_fail()");
        try {
            Map<String, Object> req = validRequest();
            req.put("creditLimit", "5000.00");
            putAccount(ACCT_1, req, false)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Update of record failed"))
                    .andExpect(jsonPath("$.field").value("acctId"));
        } finally {
            jdbc.execute("DROP TRIGGER accounts_it_fail ON customer");
            jdbc.execute("DROP FUNCTION accounts_it_fail()");
        }
        assertThat(jdbc.queryForObject("SELECT credit_limit FROM account WHERE acct_id = 1", BigDecimal.class))
                .isEqualByComparingTo("2020.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "0000000000A", "00000000000"})
    void invalidAccountNumber(String acctId) throws Exception {
        // 1210-EDIT-ACCOUNT: NOT NUMERIC OR ZEROS
        putAccount(acctId, validRequest(), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Account Number if supplied must be a 11 digit Non-Zero Number"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void blankAccountNumber() throws Exception {
        // 1210-EDIT-ACCOUNT blank -> NO-SEARCH-CRITERIA-RECEIVED
        mvc.perform(as(USER, put("/api/accounts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No input received"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void withoutSessionReturnsToSignOn() throws Exception {
        mvc.perform(put("/api/accounts/" + ACCT_1).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }
}
