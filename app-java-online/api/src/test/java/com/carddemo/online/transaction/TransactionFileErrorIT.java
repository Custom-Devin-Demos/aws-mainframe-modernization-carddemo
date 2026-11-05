package com.carddemo.online.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.domain.CardXref;
import com.carddemo.online.domain.Transaction;
import com.carddemo.online.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/** The {@code WHEN OTHER} RESP branches of COTRN00C/01C/02C, forced with a failing TransactionStore. */
class TransactionFileErrorIT extends AbstractIntegrationTest {

    @MockitoBean
    TransactionStore store;

    @Autowired
    ObjectMapper json;

    private static final DataAccessResourceFailureException IO_ERROR = new DataAccessResourceFailureException("io");

    private ResultActions add(Map<String, String> form) throws Exception {
        return mvc.perform(as(USER, post("/api/transactions")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(form)));
    }

    private void xrefFound() {
        CardXref x = new CardXref();
        x.setCardNum(TransactionAddIT.ACCT_50_CARD);
        x.setAcctId(50L);
        when(store.xrefByAccount(anyLong())).thenReturn(Optional.of(x));
    }

    @Test
    void listBrowseFailure() throws Exception {
        // STARTBR / READNEXT / READPREV: WHEN OTHER
        when(store.readFrom(anyString(), anyInt())).thenThrow(IO_ERROR);
        mvc.perform(as(USER, get("/api/transactions")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to lookup transaction..."))
                .andExpect(jsonPath("$.field").value("startTranId"));
    }

    @Test
    void viewReadFailure() throws Exception {
        // READ-TRANSACT-FILE: WHEN OTHER
        when(store.find(anyString())).thenThrow(IO_ERROR);
        mvc.perform(as(USER, get("/api/transactions/{id}", "0000000000683580")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to lookup Transaction..."))
                .andExpect(jsonPath("$.field").value("tranId"));
    }

    @Test
    void accountXrefFailure() throws Exception {
        // READ-CXACAIX-FILE: WHEN OTHER
        when(store.xrefByAccount(anyLong())).thenThrow(IO_ERROR);
        add(TransactionAddIT.validForm())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to lookup Acct in XREF AIX file..."))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void cardXrefFailure() throws Exception {
        // READ-CCXREF-FILE: WHEN OTHER
        when(store.xrefByCard(anyString())).thenThrow(IO_ERROR);
        add(TransactionAddIT.with("acctId", "", "cardNum", TransactionAddIT.ACCT_50_CARD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to lookup Card # in XREF file..."))
                .andExpect(jsonPath("$.field").value("cardNum"));
    }

    @Test
    void highestTranIdLookupFailure() throws Exception {
        // STARTBR / READPREV-TRANSACT-FILE (ADD-TRANSACTION): WHEN OTHER
        xrefFound();
        when(store.findLast()).thenThrow(IO_ERROR);
        add(TransactionAddIT.validForm())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to lookup Transaction..."))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void duplicateTranId() throws Exception {
        // WRITE-TRANSACT-FILE: DUPKEY / DUPREC
        xrefFound();
        when(store.findLast()).thenReturn(Optional.empty());
        doThrow(new DuplicateKeyException("dup")).when(store).insert(any(Transaction.class));
        add(TransactionAddIT.validForm())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tran ID already exist..."))
                .andExpect(jsonPath("$.field").value("acctId"));
    }

    @Test
    void writeFailure() throws Exception {
        // WRITE-TRANSACT-FILE: WHEN OTHER
        xrefFound();
        when(store.findLast()).thenReturn(Optional.empty());
        doThrow(IO_ERROR).when(store).insert(any(Transaction.class));
        add(TransactionAddIT.validForm())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to Add Transaction..."))
                .andExpect(jsonPath("$.field").value("acctId"));
    }
}
