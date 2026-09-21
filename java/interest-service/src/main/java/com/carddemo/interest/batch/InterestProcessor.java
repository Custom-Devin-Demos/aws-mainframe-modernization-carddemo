package com.carddemo.interest.batch;

import com.carddemo.interest.calc.InterestCalculator;
import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.CardXref;
import com.carddemo.interest.domain.TranCatBalance;
import com.carddemo.interest.domain.Transaction;
import com.carddemo.interest.repository.AccountRepository;
import com.carddemo.interest.repository.CardXrefRepository;
import com.carddemo.interest.service.AccountPostingService;
import com.carddemo.interest.service.InterestRateService;
import com.carddemo.interest.service.InterestServiceException;
import com.carddemo.interest.service.TransactionFactory;
import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.batch.item.ItemProcessor;

/**
 * Control-break driver of CBACT04C main loop (lines 188-222).
 *
 * <p>Reads TCATBAL sequentially; on TRANCAT-ACCT-ID change (and at end-of-file) posts the accrued
 * WS-TOTAL-INT to the previous account, resets the total, loads the new Account + CardXref.
 * Per record: look up rate, and if non-zero compute interest and emit one Transaction.
 *
 * <p>Stateful and single-threaded, exactly like the COBOL working storage it mirrors. One instance
 * must be created per job execution (step scope).
 */
@Trace(program = "CBACT04C", paragraph = "PROCEDURE DIVISION main loop", lines = "188-222")
public class InterestProcessor implements ItemProcessor<TranCatBalance, InterestItemResult> {

    private final String runDate;
    private final InterestCalculator calculator;
    private final InterestRateService rateService;
    private final TransactionFactory transactionFactory;
    private final AccountPostingService postingService;
    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final Supplier<String> timestamps;

    @Trace(program = "CBACT04C", lines = "167", note = "WS-LAST-ACCT-NUM PIC 9(11)")
    private String lastAcctId;
    @Trace(program = "CBACT04C", lines = "169", note = "WS-TOTAL-INT PIC S9(09)V99")
    private BigDecimal totalInterest = zero();
    @Trace(program = "CBACT04C", lines = "170", note = "WS-FIRST-TIME PIC X(01) VALUE 'Y'")
    private boolean firstTime = true;
    @Trace(program = "CBACT04C", lines = "172", note = "WS-RECORD-COUNT PIC 9(09)")
    private long recordCount;
    @Trace(program = "CBACT04C", lines = "173", note = "WS-TRANID-SUFFIX PIC 9(06)")
    private long tranIdSuffix;

    /** ACCOUNT-RECORD / CARD-XREF-RECORD currently loaded (1100-GET-ACCT-DATA / 1110-GET-XREF-DATA). */
    private Account currentAccount;
    private CardXref currentXref;

    public InterestProcessor(String runDate,
                             InterestCalculator calculator,
                             InterestRateService rateService,
                             TransactionFactory transactionFactory,
                             AccountPostingService postingService,
                             AccountRepository accounts,
                             CardXrefRepository xrefs,
                             Supplier<String> timestamps) {
        this.runDate = runDate;
        this.calculator = calculator;
        this.rateService = rateService;
        this.transactionFactory = transactionFactory;
        this.postingService = postingService;
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.timestamps = timestamps;
    }

    @Override
    @Trace(program = "CBACT04C", lines = "191-218")
    public InterestItemResult process(TranCatBalance item) {
        recordCount++;
        List<Account> accountUpdates = List.of();

        if (!item.acctId().equals(lastAcctId)) {
            if (!firstTime) {
                accountUpdates = List.of(updateAccount());
            } else {
                firstTime = false;
            }
            totalInterest = zero();
            lastAcctId = item.acctId();
            currentAccount = getAccountData(item.acctId());
            currentXref = getXrefData(item.acctId());
        }

        BigDecimal rate = rateService.getRate(currentAccount.groupId(), item.typeCd(), item.catCd());
        List<Transaction> transactions = List.of();
        if (rate.signum() != 0) {
            transactions = List.of(computeInterest(item, rate));
            postingService.computeFees(currentAccount);
        }
        return new InterestItemResult(transactions, accountUpdates);
    }

    /** End-of-file branch (line 220): flush the last account. */
    @Trace(program = "CBACT04C", lines = "219-221")
    public InterestItemResult flush() {
        if (currentAccount == null) {
            return InterestItemResult.empty();
        }
        InterestItemResult result = new InterestItemResult(List.of(), List.of(updateAccount()));
        currentAccount = null;
        return result;
    }

    /** WS-RECORD-COUNT */
    public long getRecordCount() {
        return recordCount;
    }

    /** WS-TRANID-SUFFIX */
    public long getTranIdSuffix() {
        return tranIdSuffix;
    }

    @Trace(program = "CBACT04C", paragraph = "1050-UPDATE-ACCOUNT", lines = "350-356")
    private Account updateAccount() {
        Account updated = postingService.postInterest(currentAccount, totalInterest);
        currentAccount = updated;
        return updated;
    }

    @Trace(program = "CBACT04C", paragraph = "1300-COMPUTE-INTEREST", lines = "462-470")
    private Transaction computeInterest(TranCatBalance item, BigDecimal rate) {
        BigDecimal monthlyInterest = calculator.computeMonthlyInterest(item.balance(), rate);
        totalInterest = calculator.accumulate(totalInterest, monthlyInterest);
        return writeTx(monthlyInterest);
    }

    @Trace(program = "CBACT04C", paragraph = "1300-B-WRITE-TX", lines = "473-500")
    private Transaction writeTx(BigDecimal monthlyInterest) {
        tranIdSuffix++;
        return transactionFactory.interestTransaction(
                runDate, tranIdSuffix, currentAccount, currentXref, monthlyInterest, timestamps.get());
    }

    @Trace(program = "CBACT04C", paragraph = "1100-GET-ACCT-DATA", lines = "372-392")
    private Account getAccountData(String acctId) {
        return accounts.findById(acctId).orElseThrow(() -> new InterestServiceException(
                "ERROR READING ACCOUNT FILE: account " + acctId + " not found"));
    }

    @Trace(program = "CBACT04C", paragraph = "1110-GET-XREF-DATA", lines = "393-413")
    private CardXref getXrefData(String acctId) {
        Optional<CardXref> xref = xrefs.findByAcctId(acctId);
        return xref.orElseThrow(() -> new InterestServiceException(
                "ERROR READING XREF FILE: account " + acctId + " not found"));
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(InterestCalculator.SCALE);
    }
}
