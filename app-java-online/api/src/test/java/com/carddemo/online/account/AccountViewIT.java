package com.carddemo.online.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** GET /api/accounts/{acctId}: COACTVWC (default) and COACTUPC (mode=update) fetch. */
class AccountViewIT extends AbstractAccountIT {

    @AfterEach
    void reset() {
        resetSampleData();
    }

    @Test
    void withoutSessionReturnsToSignOn() throws Exception {
        // EIBCALEN = 0
        mvc.perform(get("/api/accounts/" + ACCT_1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }

    @Test
    void showsAccountAndCustomerThroughXref() throws Exception {
        // 9000-READ-ACCT -> 1200-SETUP-SCREEN-VARS
        mvc.perform(as(USER, get("/api/accounts/" + ACCT_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.field").doesNotExist())
                .andExpect(jsonPath("$.nextProgram").doesNotExist())
                .andExpect(jsonPath("$.data.acctId").value(ACCT_1))
                .andExpect(jsonPath("$.data.activeStatus").value("Y"))
                .andExpect(jsonPath("$.data.currBal").value(194.00))
                .andExpect(jsonPath("$.data.creditLimit").value(2020.00))
                .andExpect(jsonPath("$.data.cashCreditLimit").value(1020.00))
                .andExpect(jsonPath("$.data.openDate").value("2014-11-20"))
                .andExpect(jsonPath("$.data.expirationDate").value("2025-05-20"))
                .andExpect(jsonPath("$.data.cardNum").value("9680294154603697"))
                .andExpect(jsonPath("$.data.custId").value("000000001"))
                .andExpect(jsonPath("$.data.ssn").value("020973888"))
                .andExpect(jsonPath("$.data.dob").value("1961-06-08"))
                .andExpect(jsonPath("$.data.ficoScore").value("274"))
                .andExpect(jsonPath("$.data.firstName").value("Immanuel"))
                .andExpect(jsonPath("$.data.middleName").value("Madeline"))
                .andExpect(jsonPath("$.data.lastName").value("Kessler"))
                .andExpect(jsonPath("$.data.city").value("Altenwerthshire"))
                .andExpect(jsonPath("$.data.state").value("NC"))
                .andExpect(jsonPath("$.data.country").value("USA"))
                .andExpect(jsonPath("$.data.phone1").value("(908)119-8310"))
                .andExpect(jsonPath("$.data.eftAccountId").value("0053581756"))
                .andExpect(jsonPath("$.data.primaryCardHolder").value("Y"))
                .andExpect(jsonPath("$.data.version").isNotEmpty());
    }

    @Test
    void adminCanViewToo() throws Exception {
        mvc.perform(as(ADMIN, get("/api/accounts/" + ACCT_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acctId").value(ACCT_1));
    }

    @Test
    void updateModePromptsForChanges() throws Exception {
        // COACTUPC 3250-SETUP-INFOMSG: ACUP-SHOW-DETAILS -> PROMPT-FOR-CHANGES
        mvc.perform(as(USER, get("/api/accounts/" + ACCT_1).param("mode", "update")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Update account details presented above."))
                .andExpect(jsonPath("$.messageType").value("INFO"))
                .andExpect(jsonPath("$.data.custId").value("000000001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "*"})
    void blankAccountIsNoInput(String acctId) throws Exception {
        // 2200-EDIT-MAP-INPUTS: FLG-ACCTFILTER-BLANK -> NO-SEARCH-CRITERIA-RECEIVED
        mvc.perform(as(USER, acctId.isEmpty() ? get("/api/accounts") : get("/api/accounts/{id}", acctId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No input received"))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void blankAccountIsNoInputInUpdateModeToo() throws Exception {
        // COACTUPC 1200-EDIT-MAP-INPUTS: FLG-ACCTFILTER-BLANK -> NO-SEARCH-CRITERIA-RECEIVED
        mvc.perform(as(USER, get("/api/accounts").param("mode", "update")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No input received"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0000000000A", "00000000000", "-0000000001"})
    void nonNumericOrZeroAccount(String acctId) throws Exception {
        // 2210-EDIT-ACCOUNT: NOT NUMERIC OR ZEROES (X(11) padded with blanks, so short ids fail too)
        mvc.perform(as(USER, get("/api/accounts/" + acctId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account Filter must  be a non-zero 11 digit number"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0000000000A", "00000000000"})
    void nonNumericOrZeroAccountInUpdateMode(String acctId) throws Exception {
        // COACTUPC 1210-EDIT-ACCOUNT
        mvc.perform(as(USER, get("/api/accounts/" + acctId).param("mode", "update")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Account Number if supplied must be a 11 digit Non-Zero Number"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void accountNotInCrossReference() throws Exception {
        // 9200-GETCARDXREF-BYACCT: DFHRESP(NOTFND)
        mvc.perform(as(USER, get("/api/accounts/99999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Account:99999999999 not found in Cross ref file.  Resp:000000013  Reas:000000080"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void accountNotInAccountMaster() throws Exception {
        // 9300-GETACCTDATA-BYACCT: DFHRESP(NOTFND)
        insertUnchecked("INSERT INTO card_xref (card_num, cust_id, acct_id) VALUES ('9999999999999991', 1, 99999999991)");
        mvc.perform(as(USER, get("/api/accounts/99999999991").param("mode", "update")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Account:99999999991 not found in Acct Master file.Resp:000000013  Reas:000000080"))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void customerNotInCustomerMaster() throws Exception {
        // 9400-GETCUSTDATA-BYCUST: DFHRESP(NOTFND); account fields are still shown
        insertUnchecked(
                "INSERT INTO account (acct_id, active_status) VALUES (99999999992, 'Y')",
                "INSERT INTO card_xref (card_num, cust_id, acct_id) VALUES ('9999999999999992', 999999992, 99999999992)");
        mvc.perform(as(USER, get("/api/accounts/99999999992")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "CustId:999999992 not found in customer master.Resp: 000000013  REAS:000000080"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.data.acctId").value("99999999992"))
                .andExpect(jsonPath("$.data.activeStatus").value("Y"));
    }
}
