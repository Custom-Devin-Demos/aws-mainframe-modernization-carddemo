package com.carddemo.intcalc;

import static com.carddemo.intcalc.TestData.account;
import static com.carddemo.intcalc.TestData.rate;
import static com.carddemo.intcalc.TestData.tcat;
import static com.carddemo.intcalc.TestData.xref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Rules R1, R5-R10 and R12: the CBACT04C control-break loop. */
class InterestCalculationJobTest {

    private static final String A1 = "00000000001";
    private static final String A2 = "00000000002";
    private static final String A3 = "00000000003";
    private static final LocalDateTime TS = LocalDateTime.of(2022, 7, 18, 1, 2, 3, 160_000_000);

    private static final List<DisclosureGroup> RATES = List.of(
            rate("GOLD", "01", 1, "12.00"),
            rate("GOLD", "01", 2, "24.00"),
            rate("GOLD", "02", 1, "0.00"),
            rate("DEFAULT", "01", 1, "6.00"),
            rate("DEFAULT", "01", 2, "18.00"),
            rate("DEFAULT", "03", 1, "36.00"));

    private static InterestCalculationJob.Result run(boolean updateLast, List<TranCatBalance> balances,
                                                     List<Account> accounts, List<CardXref> xrefs) {
        JobConfig config = new JobConfig("2022071800", TS, updateLast);
        PrintStream sink = new PrintStream(new ByteArrayOutputStream());
        return new InterestCalculationJob(config, sink).run(balances, xrefs, accounts, RATES);
    }

    private static InterestCalculationJob.Result runDefault(List<TranCatBalance> balances) {
        return run(false, balances,
                List.of(account(A1, "1000.00", "11.11", "22.22", "GOLD"),
                        account(A2, "-50.00", "1.00", "2.00", "NOGROUP"),
                        account(A3, "300.00", "3.00", "4.00", "GOLD")),
                List.of(xref(A1, "4000000000000001"), xref(A2, "4000000000000002"),
                        xref(A3, "4000000000000003")));
    }

    private static Map<String, Account> byId(List<Account> accounts) {
        return accounts.stream().collect(Collectors.toMap(Account::acctId, Function.identity()));
    }

    // R1: records are processed in TRAN-CAT-KEY order (acct, type, cat) regardless of input order.
    @Test
    void processesBalancesInKsdsKeyOrder() {
        var result = runDefault(List.of(
                tcat(A2, "01", 1, "100.00"),
                tcat(A1, "01", 2, "100.00"),
                tcat(A1, "01", 1, "100.00"),
                tcat(A3, "01", 1, "1.00")));
        List<String> amounts = result.transactions().stream()
                .map(t -> t.description().substring(13) + ":" + t.amount()).toList();
        assertEquals(List.of(A1 + ":1.00", A1 + ":2.00", A2 + ":0.50", A3 + ":0.01"), amounts);
    }

    // R5: rate 0 -> no interest, no transaction, no TRAN-ID consumed.
    @Test
    void zeroRateCategoriesAreSkipped() {
        var result = runDefault(List.of(
                tcat(A1, "01", 1, "100.00"),
                tcat(A1, "02", 1, "999.00"),
                tcat(A1, "01", 2, "100.00"),
                tcat(A3, "01", 1, "0.00")));
        assertEquals(3, result.transactions().size());
        assertEquals(List.of("2022071800000001", "2022071800000002", "2022071800000003"),
                result.transactions().stream().map(Transaction::tranId).toList());
    }

    // R5 corollary: non-zero rate but interest truncates to 0.00 -> a 0.00 transaction is still written.
    @Test
    void zeroInterestWithNonZeroRateIsStillWritten() {
        var result = runDefault(List.of(tcat(A1, "01", 1, "0.99"), tcat(A3, "01", 1, "0.00")));
        assertEquals(new BigDecimal("0.00"), result.transactions().get(0).amount());
    }

    // R6: per-account totals are added to ACCT-CURR-BAL and cycle credit/debit reset to zero.
    @Test
    void accumulatesInterestAndUpdatesAccountOnControlBreak() {
        var result = runDefault(List.of(
                tcat(A1, "01", 1, "1000.00"),   // 12%  -> 10.00
                tcat(A1, "01", 2, "500.00"),    // 24%  -> 10.00
                tcat(A2, "01", 1, "-50.00"),    // DEFAULT 6% -> -0.25
                tcat(A3, "01", 1, "100.00")));
        Map<String, Account> accts = byId(result.accounts());
        assertEquals(new BigDecimal("1020.00"), accts.get(A1).currentBalance());
        assertEquals(new BigDecimal("0.00"), accts.get(A1).currentCycleCredit());
        assertEquals(new BigDecimal("0.00"), accts.get(A1).currentCycleDebit());
        assertEquals(new BigDecimal("-50.25"), accts.get(A2).currentBalance());
    }

    // R6 corollary: an account whose categories all have rate 0 is still rewritten (cycle reset).
    @Test
    void accountWithOnlyZeroRatesStillHasCycleTotalsReset() {
        var result = runDefault(List.of(tcat(A1, "02", 1, "999.00"), tcat(A3, "01", 1, "1.00")));
        Account a1 = byId(result.accounts()).get(A1);
        assertEquals(new BigDecimal("1000.00"), a1.currentBalance());
        assertEquals(new BigDecimal("0.00"), a1.currentCycleCredit());
        assertEquals(new BigDecimal("0.00"), a1.currentCycleDebit());
    }

    // R7: CBACT04C never rewrites the last account; parity mode reproduces that.
    @Test
    void lastAccountIsNotRewrittenInParityMode() {
        var result = runDefault(List.of(tcat(A1, "01", 1, "100.00"), tcat(A3, "01", 1, "300.00")));
        Account a3 = byId(result.accounts()).get(A3);
        assertEquals(new BigDecimal("300.00"), a3.currentBalance());
        assertEquals(new BigDecimal("3.00"), a3.currentCycleCredit());
        assertEquals(2, result.transactions().size(), "interest transaction is still written");
    }

    @Test
    void lastAccountIsRewrittenWhenFixEnabled() {
        var result = run(true, List.of(tcat(A3, "01", 1, "300.00")),
                List.of(account(A3, "300.00", "3.00", "4.00", "GOLD")),
                List.of(xref(A3, "4000000000000003")));
        Account a3 = byId(result.accounts()).get(A3);
        assertEquals(new BigDecimal("303.00"), a3.currentBalance());
        assertEquals(new BigDecimal("0.00"), a3.currentCycleDebit());
    }

    // Accounts without any TCATBALF records are passed through untouched.
    @Test
    void accountsWithoutBalancesAreUntouched() {
        var result = runDefault(List.of(tcat(A1, "01", 1, "100.00")));
        Account a2 = byId(result.accounts()).get(A2);
        assertEquals(account(A2, "-50.00", "1.00", "2.00", "NOGROUP").toRecord(), a2.toRecord());
        assertEquals(3, result.accounts().size());
    }

    // R8: interest transaction content.
    @Test
    void interestTransactionFields() {
        var result = runDefault(List.of(tcat(A1, "01", 1, "1234.56"), tcat(A3, "01", 1, "1.00")));
        Transaction t = result.transactions().get(0);
        assertEquals("2022071800000001", t.tranId());
        assertEquals("01", t.typeCode());
        assertEquals("0005", t.catCode());
        assertEquals("System", t.source());
        assertEquals("Int. for a/c " + A1, t.description());
        assertEquals(new BigDecimal("12.34"), t.amount());
        assertEquals(0L, t.merchantId());
        assertEquals("", t.merchantName());
        assertEquals("4000000000000001", t.cardNum());
        assertEquals("2022-07-18-01.02.03.160000", t.origTimestamp());
        assertEquals(t.origTimestamp(), t.procTimestamp());
    }

    // R8: TRAN-ID = PARM-DATE + 6-digit sequence that runs across accounts (not reset per account).
    @Test
    void tranIdSequenceSpansAccounts() {
        var result = runDefault(List.of(
                tcat(A1, "01", 1, "1.00"), tcat(A2, "01", 1, "1.00"), tcat(A3, "01", 1, "1.00")));
        assertEquals(List.of("2022071800000001", "2022071800000002", "2022071800000003"),
                result.transactions().stream().map(Transaction::tranId).toList());
    }

    // R9: card number comes from the xref; duplicate alternate keys resolve to the lowest card number.
    @Test
    void cardNumberIsFirstXrefCardForAccount() {
        var result = run(false, List.of(tcat(A1, "01", 1, "100.00")),
                List.of(account(A1, "0.00", "0.00", "0.00", "GOLD")),
                List.of(xref(A1, "4000000000000009"), xref(A1, "4000000000000005")));
        assertEquals("4000000000000005", result.transactions().get(0).cardNum());
    }

    // R10: missing master data abends, as 1100/1110/1200-A do.
    @Test
    void missingAccountXrefOrDefaultRateAbends() {
        List<Account> accts = List.of(account(A1, "0.00", "0.00", "0.00", "GOLD"));
        List<CardXref> xrefs = List.of(xref(A1, "4000000000000001"));
        assertThrows(IntCalcException.class,
                () -> run(false, List.of(tcat(A2, "01", 1, "1.00")), accts, xrefs));
        assertThrows(IntCalcException.class,
                () -> run(false, List.of(tcat(A1, "01", 1, "1.00")), accts, List.of()));
        assertThrows(IntCalcException.class,
                () -> run(false, List.of(tcat(A1, "09", 9, "1.00")), accts, xrefs));
    }

    // R12: 1400-COMPUTE-FEES is a stub; only interest transactions (type 01 / cat 0005) are produced.
    @Test
    void noFeeTransactionsAreProduced() {
        var result = runDefault(List.of(
                tcat(A1, "01", 1, "100.00"), tcat(A1, "01", 2, "100.00"), tcat(A3, "01", 1, "100.00")));
        assertTrue(result.transactions().stream()
                .allMatch(t -> t.typeCode().equals("01") && t.catCode().equals("0005")));
        assertEquals(3, result.transactions().size());
    }

    @Test
    void emptyInputProducesNoOutputAndNoUpdates() {
        var result = runDefault(List.of());
        assertEquals(0, result.transactions().size());
        assertEquals(new BigDecimal("1000.00"), byId(result.accounts()).get(A1).currentBalance());
    }
}
