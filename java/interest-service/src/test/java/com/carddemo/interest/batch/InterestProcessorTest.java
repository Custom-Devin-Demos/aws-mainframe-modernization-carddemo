package com.carddemo.interest.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.interest.calc.InterestCalculator;
import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.CardXref;
import com.carddemo.interest.domain.DisclosureGroup;
import com.carddemo.interest.domain.TranCatBalance;
import com.carddemo.interest.domain.Transaction;
import com.carddemo.interest.repository.InMemoryAccountRepository;
import com.carddemo.interest.repository.InMemoryCardXrefRepository;
import com.carddemo.interest.repository.InMemoryDisclosureGroupRepository;
import com.carddemo.interest.service.AccountPostingService;
import com.carddemo.interest.service.InterestRateService;
import com.carddemo.interest.service.InterestServiceException;
import com.carddemo.interest.service.TransactionFactory;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterestProcessorTest {

    private static final String RUN_DATE = "2022071800";
    private static final String TS = "2022-07-18-10.15.30.120000";
    private static final String ACCT_1 = "00000000010";
    private static final String ACCT_2 = "00000000011";

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryCardXrefRepository xrefs = new InMemoryCardXrefRepository();
    private final InMemoryDisclosureGroupRepository discgrps = new InMemoryDisclosureGroupRepository();
    private InterestProcessor processor;

    private static Account account(String id, String groupId, String bal) {
        return new Account(id, "Y", new BigDecimal(bal), new BigDecimal("5000.00"), new BigDecimal("1000.00"),
                "2020-01-01", "2025-01-01", "2023-01-01", new BigDecimal("50.00"), new BigDecimal("20.00"),
                "12345", groupId);
    }

    private static TranCatBalance tcatbal(String acctId, String typeCd, String catCd, String bal) {
        return new TranCatBalance(acctId, typeCd, catCd, new BigDecimal(bal));
    }

    @BeforeEach
    void setUp() {
        accounts.load(List.of(account(ACCT_1, "A000000001", "1000.00"), account(ACCT_2, "NOGROUP", "-200.00")));
        xrefs.load(List.of(new CardXref("4111111111111111", "000000001", ACCT_1),
                new CardXref("4222222222222222", "000000002", ACCT_2)));
        discgrps.load(List.of(
                new DisclosureGroup("A000000001", "01", "0001", new BigDecimal("12.00")),
                new DisclosureGroup("A000000001", "01", "0002", new BigDecimal("0.00")),
                new DisclosureGroup("DEFAULT", "01", "0001", new BigDecimal("24.00")),
                new DisclosureGroup("DEFAULT", "01", "0002", new BigDecimal("6.00"))));
        processor = new InterestProcessor(RUN_DATE, new InterestCalculator(), new InterestRateService(discgrps),
                new TransactionFactory(), new AccountPostingService(), accounts, xrefs, () -> TS);
    }

    @Test
    void mainLoop_firstRecordLoadsAccountWithoutPostingPreviousAccount() {
        InterestItemResult r = processor.process(tcatbal(ACCT_1, "01", "0001", "1000.00"));

        assertThat(r.accountUpdates()).isEmpty();
        assertThat(r.transactions()).hasSize(1);
        Transaction tx = r.transactions().get(0);
        assertThat(tx.id()).isEqualTo("2022071800000001");
        assertThat(tx.amount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(tx.cardNum()).isEqualTo("4111111111111111");
        assertThat(tx.description()).isEqualTo("Int. for a/c " + ACCT_1);
    }

    @Test
    void mainLoop_zeroRateSkipsInterestAndTransaction() {
        processor.process(tcatbal(ACCT_1, "01", "0001", "1000.00"));
        InterestItemResult r = processor.process(tcatbal(ACCT_1, "01", "0002", "1000.00"));

        assertThat(r.transactions()).isEmpty();
        assertThat(r.accountUpdates()).isEmpty();
        assertThat(processor.getTranIdSuffix()).isEqualTo(1);
        assertThat(processor.getRecordCount()).isEqualTo(2);
    }

    @Test
    void paragraph1050UpdateAccount_controlBreakPostsAccumulatedInterestAndResetsTotal() {
        processor.process(tcatbal(ACCT_1, "01", "0001", "1000.00")); // 10.00
        processor.process(tcatbal(ACCT_1, "01", "0001", "500.50"));  // 5.00 (5.005 truncated)
        InterestItemResult r = processor.process(tcatbal(ACCT_2, "01", "0001", "-200.00"));

        assertThat(r.accountUpdates()).hasSize(1);
        Account posted = r.accountUpdates().get(0);
        assertThat(posted.id()).isEqualTo(ACCT_1);
        assertThat(posted.currBal()).isEqualTo(new BigDecimal("1015.00"));
        assertThat(posted.currCycCredit()).isEqualTo(new BigDecimal("0.00"));
        assertThat(posted.currCycDebit()).isEqualTo(new BigDecimal("0.00"));

        // new account: DEFAULT fallback rate 24.00 on negative balance -> -4.00
        assertThat(r.transactions()).singleElement()
                .satisfies(tx -> {
                    assertThat(tx.amount()).isEqualTo(new BigDecimal("-4.00"));
                    assertThat(tx.id()).isEqualTo("2022071800000003");
                    assertThat(tx.cardNum()).isEqualTo("4222222222222222");
                });

        InterestItemResult eof = processor.flush();
        assertThat(eof.transactions()).isEmpty();
        assertThat(eof.accountUpdates()).singleElement()
                .satisfies(a -> {
                    assertThat(a.id()).isEqualTo(ACCT_2);
                    assertThat(a.currBal()).isEqualTo(new BigDecimal("-204.00"));
                });
    }

    @Test
    void mainLoop_endOfFileFlushPostsLastAccountOnce() {
        processor.process(tcatbal(ACCT_1, "01", "0001", "1000.00"));
        InterestItemResult eof = processor.flush();

        assertThat(eof.accountUpdates()).singleElement()
                .satisfies(a -> assertThat(a.currBal()).isEqualTo(new BigDecimal("1010.00")));
        assertThat(processor.flush()).isEqualTo(InterestItemResult.empty());
    }

    @Test
    void mainLoop_emptyInputFlushDoesNothing() {
        assertThat(processor.flush()).isEqualTo(InterestItemResult.empty());
    }

    @Test
    void paragraph1300BWriteTx_suffixIncrementsAcrossAccounts() {
        processor.process(tcatbal(ACCT_1, "01", "0001", "100.00"));
        processor.process(tcatbal(ACCT_1, "01", "0002", "100.00")); // zero rate, no suffix
        processor.process(tcatbal(ACCT_1, "01", "0001", "100.00"));
        InterestItemResult r = processor.process(tcatbal(ACCT_2, "01", "0002", "100.00"));

        assertThat(r.transactions().get(0).id()).isEqualTo("2022071800000003");
        assertThat(processor.getTranIdSuffix()).isEqualTo(3);
    }

    @Test
    void paragraph1100GetAcctData_missingAccountAbends() {
        assertThatThrownBy(() -> processor.process(tcatbal("99999999999", "01", "0001", "1.00")))
                .isInstanceOf(InterestServiceException.class)
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void paragraph1110GetXrefData_missingXrefAbends() {
        accounts.load(List.of(account("00000000099", "A000000001", "1.00")));
        assertThatThrownBy(() -> processor.process(tcatbal("00000000099", "01", "0001", "1.00")))
                .isInstanceOf(InterestServiceException.class)
                .hasMessageContaining("XREF");
    }
}
