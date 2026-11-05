package com.carddemo.online.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** COTRN02C - Add Transaction (ENTER and PF5 copy last transaction). */
class TransactionAddIT extends AbstractIntegrationTest {

    /** CARDXREF: card 0500024453765740 belongs to account 50. */
    static final String ACCT_50_CARD = "0500024453765740";

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void reset() {
        resetSampleData();
    }

    static Map<String, String> validForm() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("acctId", "50");
        f.put("cardNum", "");
        f.put("typeCd", "01");
        f.put("catCd", "0001");
        f.put("source", "POS TERM");
        f.put("description", "Test purchase");
        f.put("amount", "-00000012.34");
        f.put("origDate", "2024-01-15");
        f.put("procDate", "2024-01-16");
        f.put("merchantId", "123456789");
        f.put("merchantName", "Test Merchant");
        f.put("merchantCity", "Seattle");
        f.put("merchantZip", "98101");
        f.put("confirm", "Y");
        return f;
    }

    static Map<String, String> with(String... kv) {
        Map<String, String> f = validForm();
        for (int i = 0; i < kv.length; i += 2) {
            f.put(kv[i], kv[i + 1]);
        }
        return f;
    }

    private ResultActions submit(String path, Map<String, String> form) throws Exception {
        return mvc.perform(as(USER, post(path)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(form)));
    }

    private ResultActions add(Map<String, String> form) throws Exception {
        return submit("/api/transactions", form);
    }

    @Test
    void addsTransactionWithNextTranId() throws Exception {
        // ADD-TRANSACTION + WRITE-TRANSACT-FILE NORMAL; TRAN-ID = highest + 1
        add(validForm())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Transaction added successfully.  Your Tran ID is 0000000996722788."))
                .andExpect(jsonPath("$.messageType").value("SUCCESS"))
                .andExpect(jsonPath("$.field").value("acctId"))
                .andExpect(jsonPath("$.data.tranId").value("0000000996722788"));

        mvc.perform(as(USER, get("/api/transactions/{id}", "0000000996722788")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardNum").value(ACCT_50_CARD))
                .andExpect(jsonPath("$.data.typeCd").value("01"))
                .andExpect(jsonPath("$.data.catCd").value("0001"))
                .andExpect(jsonPath("$.data.source").value("POS TERM"))
                .andExpect(jsonPath("$.data.description").value("Test purchase"))
                .andExpect(jsonPath("$.data.amount").value("-00000012.34"))
                .andExpect(jsonPath("$.data.origDate").value("2024-01-15"))
                .andExpect(jsonPath("$.data.procDate").value("2024-01-16"))
                .andExpect(jsonPath("$.data.merchantId").value("123456789"))
                .andExpect(jsonPath("$.data.merchantName").value("Test Merchant"))
                .andExpect(jsonPath("$.data.merchantCity").value("Seattle"))
                .andExpect(jsonPath("$.data.merchantZip").value("98101"));
    }

    @Test
    void lowercaseConfirmAddsAndIdsKeepIncrementing() throws Exception {
        // PROCESS-ENTER-KEY: CONFIRMI = 'y'
        add(with("confirm", "y")).andExpect(jsonPath("$.data.tranId").value("0000000996722788"));
        add(validForm()).andExpect(jsonPath("$.data.tranId").value("0000000996722789"));
    }

    @Test
    void addsByCardNumberResolvedThroughXref() throws Exception {
        // VALIDATE-INPUT-KEY-FIELDS: card number branch, NUMVAL pads to 16 digits, READ-CCXREF-FILE
        add(with("acctId", "", "cardNum", "500024453765740")).andExpect(status().isOk());
        Long acct = jdbc.queryForObject("SELECT x.acct_id FROM transaction t JOIN card_xref x"
                + " ON x.card_num = t.card_num WHERE t.tran_id = '0000000996722788'", Long.class);
        org.assertj.core.api.Assertions.assertThat(acct).isEqualTo(50L);
    }

    @Test
    void firstTransactionInEmptyFileGetsIdOne() throws Exception {
        // READPREV-TRANSACT-FILE: ENDFILE -> TRAN-ID = ZEROS
        jdbc.update("DELETE FROM transaction");
        add(validForm()).andExpect(jsonPath("$.data.tranId").value("0000000000000001"));
    }

    static Stream<Arguments> validationBranches() {
        return Stream.of(
                // VALIDATE-INPUT-KEY-FIELDS
                Arguments.of(with("acctId", "5A"), 400, "Account ID must be Numeric...", "acctId"),
                Arguments.of(with("acctId", "99999"), 404, "Account ID NOT found...", "acctId"),
                Arguments.of(with("acctId", "", "cardNum", "4111X"), 400, "Card Number must be Numeric...", "cardNum"),
                Arguments.of(with("acctId", "", "cardNum", "1111222233334444"), 404, "Card Number NOT found...",
                        "cardNum"),
                Arguments.of(with("acctId", "", "cardNum", " "), 400, "Account or Card Number must be entered...",
                        "acctId"),
                // VALIDATE-INPUT-DATA-FIELDS: empty checks
                Arguments.of(with("typeCd", ""), 400, "Type CD can NOT be empty...", "typeCd"),
                Arguments.of(with("catCd", " "), 400, "Category CD can NOT be empty...", "catCd"),
                Arguments.of(with("source", ""), 400, "Source can NOT be empty...", "source"),
                Arguments.of(with("description", ""), 400, "Description can NOT be empty...", "description"),
                Arguments.of(with("amount", ""), 400, "Amount can NOT be empty...", "amount"),
                Arguments.of(with("origDate", ""), 400, "Orig Date can NOT be empty...", "origDate"),
                Arguments.of(with("procDate", ""), 400, "Proc Date can NOT be empty...", "procDate"),
                Arguments.of(with("merchantId", ""), 400, "Merchant ID can NOT be empty...", "merchantId"),
                Arguments.of(with("merchantName", ""), 400, "Merchant Name can NOT be empty...", "merchantName"),
                Arguments.of(with("merchantCity", ""), 400, "Merchant City can NOT be empty...", "merchantCity"),
                Arguments.of(with("merchantZip", ""), 400, "Merchant Zip can NOT be empty...", "merchantZip"),
                // VALIDATE-INPUT-DATA-FIELDS: numeric checks
                Arguments.of(with("typeCd", "A1"), 400, "Type CD must be Numeric...", "typeCd"),
                Arguments.of(with("catCd", "00X1"), 400, "Category CD must be Numeric...", "catCd"),
                // TRNAMTI(1:1) sign, (2:8) digits, (10:1) '.', (11:2) digits
                Arguments.of(with("amount", "00000012.340"), 400, "Amount should be in format -99999999.99", "amount"),
                Arguments.of(with("amount", "+0000001A.34"), 400, "Amount should be in format -99999999.99", "amount"),
                Arguments.of(with("amount", "+00000012,34"), 400, "Amount should be in format -99999999.99", "amount"),
                Arguments.of(with("amount", "+00000012.3X"), 400, "Amount should be in format -99999999.99", "amount"),
                Arguments.of(with("amount", "-12.34"), 400, "Amount should be in format -99999999.99", "amount"),
                // TORIGDTI / TPROCDTI format YYYY-MM-DD
                Arguments.of(with("origDate", "2024/01/15"), 400, "Orig Date should be in format YYYY-MM-DD",
                        "origDate"),
                Arguments.of(with("origDate", "24-01-15"), 400, "Orig Date should be in format YYYY-MM-DD", "origDate"),
                Arguments.of(with("procDate", "2024-1-16"), 400, "Proc Date should be in format YYYY-MM-DD",
                        "procDate"),
                // CSUTLDTC date validation
                Arguments.of(with("origDate", "2024-02-30"), 400, "Orig Date - Not a valid date...", "origDate"),
                Arguments.of(with("origDate", "2023-02-29"), 400, "Orig Date - Not a valid date...", "origDate"),
                Arguments.of(with("procDate", "2024-13-01"), 400, "Proc Date - Not a valid date...", "procDate"),
                Arguments.of(with("merchantId", "12345678X"), 400, "Merchant ID must be Numeric...", "merchantId"),
                // PROCESS-ENTER-KEY: CONFIRMI
                Arguments.of(with("confirm", "N"), 400, "Confirm to add this transaction...", "confirm"),
                Arguments.of(with("confirm", "n"), 400, "Confirm to add this transaction...", "confirm"),
                Arguments.of(with("confirm", ""), 400, "Confirm to add this transaction...", "confirm"),
                Arguments.of(with("confirm", "X"), 400, "Invalid value. Valid values are (Y/N)...", "confirm"),
                // Order: key fields before data fields; empty checks before format checks
                Arguments.of(with("acctId", "5A", "typeCd", ""), 400, "Account ID must be Numeric...", "acctId"),
                Arguments.of(with("typeCd", "", "catCd", "X"), 400, "Type CD can NOT be empty...", "typeCd"),
                Arguments.of(with("amount", "bad", "merchantZip", ""), 400, "Merchant Zip can NOT be empty...",
                        "merchantZip"),
                Arguments.of(with("typeCd", "AB", "amount", "bad"), 400, "Type CD must be Numeric...", "typeCd"),
                Arguments.of(with("amount", "bad", "origDate", "bad"), 400, "Amount should be in format -99999999.99",
                        "amount"),
                Arguments.of(with("procDate", "bad", "origDate", "2024-02-30"), 400,
                        "Proc Date should be in format YYYY-MM-DD", "procDate"),
                Arguments.of(with("origDate", "2024-02-30", "merchantId", "X"), 400, "Orig Date - Not a valid date...",
                        "origDate"),
                Arguments.of(with("merchantId", "X", "confirm", "X"), 400, "Merchant ID must be Numeric...",
                        "merchantId"));
    }

    @ParameterizedTest(name = "{2} ({3})")
    @MethodSource("validationBranches")
    void rejectsInvalidInput(Map<String, String> form, int status, String message, String field) throws Exception {
        add(form)
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value(field));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM transaction", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(300);
    }

    @Test
    void errorResponseCarriesResolvedKeysAndNormalisedAmount() throws Exception {
        // VALIDATE-INPUT-KEY-FIELDS moves XREF-CARD-NUM / padded ACCT-ID to the map; amount re-edited by NUMVAL-C
        add(with("acctId", "50", "amount", "-00000000.00", "confirm", ""))
                .andExpect(jsonPath("$.message").value("Confirm to add this transaction..."))
                .andExpect(jsonPath("$.data.acctId").value("00000000050"))
                .andExpect(jsonPath("$.data.cardNum").value(ACCT_50_CARD))
                .andExpect(jsonPath("$.data.amount").value("+00000000.00"));
        add(with("acctId", "", "cardNum", "500024453765740", "confirm", ""))
                .andExpect(jsonPath("$.data.acctId").value("00000000050"))
                .andExpect(jsonPath("$.data.cardNum").value(ACCT_50_CARD));
    }

    @Test
    void accountNotFoundShowsPaddedAccountId() throws Exception {
        add(with("acctId", "99999")).andExpect(jsonPath("$.data.acctId").value("00000099999"));
    }

    @Test
    void copyLastFillsFieldsFromHighestTransaction() throws Exception {
        // COPY-LAST-TRAN-DATA then PROCESS-ENTER-KEY: last sample record has a blank TRAN-PROC-TS
        submit("/api/transactions/copy-last", Map.of("acctId", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Proc Date can NOT be empty..."))
                .andExpect(jsonPath("$.field").value("procDate"))
                .andExpect(jsonPath("$.data.acctId").value("00000000050"))
                .andExpect(jsonPath("$.data.cardNum").value(ACCT_50_CARD))
                .andExpect(jsonPath("$.data.typeCd").value("01"))
                .andExpect(jsonPath("$.data.catCd").value("0001"))
                .andExpect(jsonPath("$.data.source").value("POS TERM"))
                .andExpect(jsonPath("$.data.amount").value("+00000603.22"))
                .andExpect(jsonPath("$.data.description").value("Purchase at Kilback LLC"))
                .andExpect(jsonPath("$.data.origDate").value("2022-06-10"))
                .andExpect(jsonPath("$.data.procDate").value(""))
                .andExpect(jsonPath("$.data.merchantId").value("800000000"))
                .andExpect(jsonPath("$.data.merchantName").value("Kilback LLC"))
                .andExpect(jsonPath("$.data.merchantCity").value("Cummeratamouth"))
                .andExpect(jsonPath("$.data.merchantZip").value("53200-7529"));
    }

    @Test
    void copyLastRequiresAccountOrCard() throws Exception {
        // COPY-LAST-TRAN-DATA -> VALIDATE-INPUT-KEY-FIELDS: WHEN OTHER
        submit("/api/transactions/copy-last", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account or Card Number must be entered..."))
                .andExpect(jsonPath("$.field").value("acctId"));
        submit("/api/transactions/copy-last", Map.of("acctId", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."));
    }

    @Test
    void copyLastAsksForConfirmationThenAdds() throws Exception {
        add(validForm()).andExpect(status().isOk());
        // CARDXREF: account 27 owns card 0683586198171516
        submit("/api/transactions/copy-last", Map.of("acctId", "27"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirm to add this transaction..."))
                .andExpect(jsonPath("$.field").value("confirm"))
                .andExpect(jsonPath("$.data.cardNum").value("0683586198171516"))
                .andExpect(jsonPath("$.data.description").value("Test purchase"))
                .andExpect(jsonPath("$.data.amount").value("-00000012.34"))
                .andExpect(jsonPath("$.data.origDate").value("2024-01-15"))
                .andExpect(jsonPath("$.data.merchantId").value("123456789"));
        submit("/api/transactions/copy-last", Map.of("acctId", "27", "confirm", "Y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Transaction added successfully.  Your Tran ID is 0000000996722789."));
        mvc.perform(as(USER, get("/api/transactions/{id}", "0000000996722789")))
                .andExpect(jsonPath("$.data.cardNum").value("0683586198171516"))
                .andExpect(jsonPath("$.data.description").value("Test purchase"));
    }

    @Test
    void requiresSession() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validForm())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }
}
