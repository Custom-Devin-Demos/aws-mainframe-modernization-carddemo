package com.carddemo.online.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** COTRN01C - View Transaction. */
class TransactionViewIT extends AbstractIntegrationTest {

    @Test
    void showsTransaction() throws Exception {
        // PROCESS-ENTER-KEY -> READ-TRANSACT-FILE NORMAL
        mvc.perform(as(USER, get("/api/transactions/{id}", "0000000000683580")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.field").value("tranId"))
                .andExpect(jsonPath("$.data.tranId").value("0000000000683580"))
                .andExpect(jsonPath("$.data.cardNum").value("4859452612877065"))
                .andExpect(jsonPath("$.data.typeCd").value("01"))
                .andExpect(jsonPath("$.data.catCd").value("0001"))
                .andExpect(jsonPath("$.data.source").value("POS TERM"))
                .andExpect(jsonPath("$.data.amount").value("+00000504.77"))
                .andExpect(jsonPath("$.data.description").value("Purchase at Abshire-Lowe"))
                .andExpect(jsonPath("$.data.origDate").value("2022-06-10"))
                .andExpect(jsonPath("$.data.procDate").value(""))
                .andExpect(jsonPath("$.data.merchantId").value("800000000"))
                .andExpect(jsonPath("$.data.merchantName").value("Abshire-Lowe"))
                .andExpect(jsonPath("$.data.merchantCity").value("North Enoshaven"))
                .andExpect(jsonPath("$.data.merchantZip").value("72112"));
    }

    @Test
    void adminCanViewToo() throws Exception {
        mvc.perform(as(ADMIN, get("/api/transactions/{id}", "0000000996722787")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merchantZip").value("53200-7529"));
    }

    @Test
    void tranIdCanNotBeEmpty() throws Exception {
        // PROCESS-ENTER-KEY: TRNIDINI = SPACES
        mvc.perform(as(USER, get("/api/transactions/{id}", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tran ID can NOT be empty..."))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value("tranId"));
    }

    @Test
    void unknownTranIdIsNotFound() throws Exception {
        // READ-TRANSACT-FILE: NOTFND
        mvc.perform(as(USER, get("/api/transactions/{id}", "0000000000000001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction ID NOT found..."))
                .andExpect(jsonPath("$.field").value("tranId"));
    }

    @Test
    void keyIsMatchedExactlyWithoutZeroPadding() throws Exception {
        // READ-TRANSACT-FILE uses TRNIDINI as typed (X(16) key, no NUMVAL)
        mvc.perform(as(USER, get("/api/transactions/{id}", "683580")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction ID NOT found..."));
    }

    @Test
    void requiresSession() throws Exception {
        mvc.perform(get("/api/transactions/{id}", "0000000000683580"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }
}
