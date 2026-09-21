package com.carddemo.interest.service;

import com.carddemo.interest.domain.Account;
import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** 1050-UPDATE-ACCOUNT and the 1400-COMPUTE-FEES placeholder. */
@Service
public class AccountPostingService {

    /**
     * ADD WS-TOTAL-INT TO ACCT-CURR-BAL; MOVE 0 TO ACCT-CURR-CYC-CREDIT / ACCT-CURR-CYC-DEBIT.
     *
     * @return the updated account record to be rewritten
     */
    @Trace(program = "CBACT04C", paragraph = "1050-UPDATE-ACCOUNT", lines = "350-356")
    public Account postInterest(Account account, BigDecimal totalInterest) {
        throw new UnsupportedOperationException("Coordinator integration step 1");
    }

    /** 1400-COMPUTE-FEES: "To be implemented" in COBOL; intentionally a no-op. */
    @Trace(program = "CBACT04C", paragraph = "1400-COMPUTE-FEES", lines = "518-520", note = "To be implemented")
    public void computeFees(Account account) {
        throw new UnsupportedOperationException("Coordinator integration step 1");
    }
}
