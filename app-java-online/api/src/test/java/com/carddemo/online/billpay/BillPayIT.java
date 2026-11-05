package com.carddemo.online.billpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class BillPayIT extends AbstractIntegrationTest {

    /** Sample account 00000000001: balance 194.00, card 9680294154603697. */
    private static final String ACCT = "00000000001";
    private static final long ACCT_ID = 1L;
    private static final String CARD = "9680294154603697";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void reset() {
        resetSampleData();
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS billpay_it_fail ON transaction");
        jdbc.execute("DROP TRIGGER IF EXISTS billpay_it_fail ON account");
        jdbc.execute("DROP FUNCTION IF EXISTS billpay_it_fail()");
    }

    private ResultActions pay(String acctId, String confirm) throws Exception {
        String body = json.writeValueAsString(new BillPayRequest(acctId, confirm));
        return mvc.perform(as(USER, post("/api/bill-payments").contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("SELECT curr_bal FROM account WHERE acct_id = ?", BigDecimal.class, ACCT_ID);
    }

    private int transactionCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transaction", Integer.class);
    }

    private String maxTranId() {
        return jdbc.queryForObject("SELECT MAX(tran_id) FROM transaction", String.class);
    }

    /** Makes every INSERT/UPDATE on {@code table} fail with the given SQLSTATE. */
    private void failOn(String table, String operation, String sqlState) {
        jdbc.execute("CREATE FUNCTION billpay_it_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
                + " RAISE EXCEPTION 'forced by BillPayIT' USING ERRCODE = '" + sqlState + "'; END $$");
        jdbc.execute("CREATE TRIGGER billpay_it_fail BEFORE " + operation + " ON " + table
                + " FOR EACH ROW EXECUTE FUNCTION billpay_it_fail()");
    }

    private void assertNothingChanged(int transactionsBefore, String maxBefore) {
        assertThat(balance()).isEqualByComparingTo("194.00");
        assertThat(transactionCount()).isEqualTo(transactionsBefore);
        assertThat(maxTranId()).isEqualTo(maxBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction WHERE description = ?", Integer.class,
                "BILL PAYMENT - ONLINE")).isZero();
    }

    @Test
    void requiresSignOn() throws Exception {
        mvc.perform(get("/api/bill-payments/" + ACCT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }

    @Test
    void getShowsBalanceAndAsksForConfirmation() throws Exception {
        // PROCESS-ENTER-KEY: CONFIRMI = SPACES -> READ-ACCTDAT-FILE, 'Confirm to make a bill payment...'
        mvc.perform(as(USER, get("/api/bill-payments/" + ACCT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acctId").value(ACCT))
                .andExpect(jsonPath("$.data.currentBalance").value(194.00))
                .andExpect(jsonPath("$.message").value("Confirm to make a bill payment..."))
                .andExpect(jsonPath("$.messageType").value("INFO"))
                .andExpect(jsonPath("$.field").value("confirm"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void postWithBlankConfirmShowsBalance() throws Exception {
        // PROCESS-ENTER-KEY: WHEN SPACES / LOW-VALUES
        pay(ACCT, " ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance").value(194.00))
                .andExpect(jsonPath("$.message").value("Confirm to make a bill payment..."))
                .andExpect(jsonPath("$.field").value("confirm"));
        assertThat(balance()).isEqualByComparingTo("194.00");
    }

    @Test
    void getUnknownAccount() throws Exception {
        // READ-ACCTDAT-FILE: DFHRESP(NOTFND)
        mvc.perform(as(USER, get("/api/bill-payments/99999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void emptyAcctId() throws Exception {
        // PROCESS-ENTER-KEY: ACTIDINI = SPACES OR LOW-VALUES
        pay("   ", "Y")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Acct ID can NOT be empty..."))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
        pay(null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Acct ID can NOT be empty..."));
    }

    @Test
    void emptyAcctIdIsCheckedBeforeConfirm() throws Exception {
        // PROCESS-ENTER-KEY: the Acct ID check comes before EVALUATE CONFIRMI
        pay("", "X")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Acct ID can NOT be empty..."))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void invalidConfirmValue() throws Exception {
        // PROCESS-ENTER-KEY: EVALUATE CONFIRMI WHEN OTHER (checked before the account is read)
        pay("99999999999", "X")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value. Valid values are (Y/N)..."))
                .andExpect(jsonPath("$.field").value("confirm"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void confirmNoClearsScreen() throws Exception {
        // PROCESS-ENTER-KEY: WHEN 'N' / 'n' -> CLEAR-CURRENT-SCREEN, no message
        for (String n : new String[] {"N", "n"}) {
            pay(ACCT, n)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.message").doesNotExist())
                    .andExpect(jsonPath("$.field").value("acctId"))
                    .andExpect(jsonPath("$.nextProgram").doesNotExist());
        }
        assertThat(balance()).isEqualByComparingTo("194.00");
    }

    @Test
    void accountNotFound() throws Exception {
        // READ-ACCTDAT-FILE: DFHRESP(NOTFND)
        pay("12345678901", "Y")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."))
                .andExpect(jsonPath("$.field").value("acctId"));
        // READ-ACCTDAT-FILE: a non-numeric ACTIDINI never matches an ACCT-ID 9(11) key
        pay("ABC", "Y")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void accountLookupFailure() throws Exception {
        // READ-ACCTDAT-FILE: WHEN OTHER
        jdbc.execute("ALTER TABLE account RENAME TO account_it");
        try {
            pay(ACCT, "Y")
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Unable to lookup Account..."))
                    .andExpect(jsonPath("$.field").value("acctId"))
                    .andExpect(jsonPath("$.nextProgram").doesNotExist());
        } finally {
            jdbc.execute("ALTER TABLE account_it RENAME TO account");
        }
    }

    @Test
    void nothingToPay() throws Exception {
        // PROCESS-ENTER-KEY: ACCT-CURR-BAL <= ZEROS
        for (String bal : new String[] {"0.00", "-12.50"}) {
            jdbc.update("UPDATE account SET curr_bal = ?::numeric WHERE acct_id = ?", bal, ACCT_ID);
            for (String confirm : new String[] {"Y", ""}) {
                pay(ACCT, confirm)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value("You have nothing to pay..."))
                        .andExpect(jsonPath("$.field").value("acctId"))
                        .andExpect(jsonPath("$.data.currentBalance").value(Double.parseDouble(bal)))
                        .andExpect(jsonPath("$.nextProgram").doesNotExist());
            }
        }
    }

    @Test
    void paymentSucceeds() throws Exception {
        int before = transactionCount();
        String expectedId = String.format("%016d", Long.parseLong(maxTranId()) + 1);

        // PROCESS-ENTER-KEY (CONF-PAY-YES) -> WRITE-TRANSACT-FILE NORMAL
        pay(ACCT, "y")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Payment successful.  Your Transaction ID is " + expectedId + "."))
                .andExpect(jsonPath("$.messageType").value("SUCCESS"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist())
                .andExpect(jsonPath("$.data.tranId").value(expectedId))
                .andExpect(jsonPath("$.data.cardNum").value(CARD))
                .andExpect(jsonPath("$.data.amount").value(194.00))
                .andExpect(jsonPath("$.data.currentBalance").value(0));

        assertThat(balance()).isEqualByComparingTo("0.00");
        assertThat(transactionCount()).isEqualTo(before + 1);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM transaction WHERE tran_id = ?", expectedId);
        assertThat(row.get("tran_type_cd")).isEqualTo("02");
        assertThat(row.get("tran_cat_cd")).isEqualTo(2);
        assertThat(row.get("source")).isEqualTo("POS TERM");
        assertThat(row.get("description")).isEqualTo("BILL PAYMENT - ONLINE");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("194.00");
        assertThat(row.get("card_num")).isEqualTo(CARD);
        assertThat(row.get("merchant_id")).isEqualTo(999999999L);
        assertThat(row.get("merchant_name")).isEqualTo("BILL PAYMENT");
        assertThat(row.get("merchant_city")).isEqualTo("N/A");
        assertThat(row.get("merchant_zip")).isEqualTo("N/A");
        assertThat(row.get("orig_ts")).isNotNull().isEqualTo(row.get("proc_ts"));

        // a second payment: the balance is now zero
        pay(ACCT, "Y")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You have nothing to pay..."));
    }

    @Test
    void xrefNotFound() throws Exception {
        int before = transactionCount();
        String max = maxTranId();
        jdbc.update("DELETE FROM card_xref WHERE acct_id = ?", ACCT_ID);
        // READ-CXACAIX-FILE: DFHRESP(NOTFND)
        pay(ACCT, "Y")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."))
                .andExpect(jsonPath("$.field").value("acctId"));
        assertNothingChanged(before, max);
    }

    @Test
    void xrefLookupFailure() throws Exception {
        // READ-CXACAIX-FILE: WHEN OTHER
        jdbc.execute("ALTER TABLE card_xref RENAME TO card_xref_it");
        try {
            pay(ACCT, "Y")
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Unable to lookup XREF AIX file..."))
                    .andExpect(jsonPath("$.field").value("acctId"));
        } finally {
            jdbc.execute("ALTER TABLE card_xref_it RENAME TO card_xref");
        }
        assertThat(balance()).isEqualByComparingTo("194.00");
    }

    @Test
    void emptyTransactionFile() throws Exception {
        jdbc.execute("DELETE FROM transaction");
        // STARTBR-TRANSACT-FILE: DFHRESP(NOTFND)
        pay(ACCT, "Y")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction ID NOT found..."))
                .andExpect(jsonPath("$.field").value("acctId"));
        assertThat(balance()).isEqualByComparingTo("194.00");
        assertThat(transactionCount()).isZero();
    }

    @Test
    void transactionLookupFailure() throws Exception {
        // STARTBR-TRANSACT-FILE / READPREV-TRANSACT-FILE: WHEN OTHER
        jdbc.execute("ALTER TABLE transaction RENAME TO transaction_it");
        try {
            pay(ACCT, "Y")
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Unable to lookup Transaction..."))
                    .andExpect(jsonPath("$.field").value("acctId"));
        } finally {
            jdbc.execute("ALTER TABLE transaction_it RENAME TO transaction");
        }
        assertThat(balance()).isEqualByComparingTo("194.00");
    }

    @Test
    void duplicateTransactionId() throws Exception {
        int before = transactionCount();
        String max = maxTranId();
        failOn("transaction", "INSERT", "23505");
        // WRITE-TRANSACT-FILE: DFHRESP(DUPKEY) / DFHRESP(DUPREC)
        pay(ACCT, "Y")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tran ID already exist..."))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
        assertNothingChanged(before, max);
    }

    @Test
    void insertFailureRollsBackEverything() throws Exception {
        int before = transactionCount();
        String max = maxTranId();
        failOn("transaction", "INSERT", "P0001");
        // WRITE-TRANSACT-FILE: WHEN OTHER -> no balance change, no transaction row
        pay(ACCT, "Y")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to Add Bill pay Transaction..."))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
        assertNothingChanged(before, max);
    }

    @Test
    void balanceUpdateFailureRollsBackTheInsertedTransaction() throws Exception {
        int before = transactionCount();
        String max = maxTranId();
        failOn("account", "UPDATE", "P0001");
        // UPDATE-ACCTDAT-FILE: WHEN OTHER -> the transaction already written is rolled back with it
        pay(ACCT, "Y")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to Update Account..."))
                .andExpect(jsonPath("$.field").value("acctId"));
        assertNothingChanged(before, max);
    }
}
