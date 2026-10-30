package com.carddemo.intcalc;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Port of the CBACT04C main loop (control break on TRANCAT-ACCT-ID). */
public final class InterestCalculationJob {

    static final String INTEREST_TYPE_CD = "01";
    static final String INTEREST_CAT_CD = "0005";
    static final String SOURCE = "System";
    static final String DESCRIPTION_PREFIX = "Int. for a/c ";
    private static final int TRANID_SUFFIX_MODULUS = 1_000_000;

    /** Output of a run: SYSTRAN records and the full account file after rewrites. */
    public record Result(List<Transaction> transactions, List<Account> accounts, int recordsRead) {
    }

    private final JobConfig config;
    private final PrintStream log;

    public InterestCalculationJob(JobConfig config, PrintStream log) {
        this.config = config;
        this.log = log;
    }

    public Result run(List<TranCatBalance> balances, List<CardXref> xrefs,
                      List<Account> accounts, List<DisclosureGroup> groups) {
        log.println("START OF EXECUTION OF PROGRAM CBACT04C (Java)");
        Map<String, Account> accountFile = new TreeMap<>();
        for (Account a : accounts) {
            if (accountFile.putIfAbsent(a.acctId(), a) != null) {
                throw new IntCalcException("DUPLICATE ACCOUNT KEY " + a.acctId());
            }
        }
        Map<String, CardXref> xrefByAcct = firstCardPerAccount(xrefs);
        InterestRateTable rates = new InterestRateTable(groups);

        List<TranCatBalance> ordered = new ArrayList<>(balances);
        ordered.sort(Comparator.comparing(TranCatBalance::key));

        String timestamp = config.db2Timestamp();
        List<Transaction> transactions = new ArrayList<>();
        Account current = null;
        CardXref currentXref = null;
        BigDecimal totalInterest = BigDecimal.ZERO;
        int tranIdSuffix = 0;

        for (TranCatBalance bal : ordered) {
            if (current == null || !bal.acctId().equals(current.acctId())) {
                if (current != null) {
                    current.applyInterest(totalInterest);
                }
                totalInterest = BigDecimal.ZERO;
                current = accountFile.get(bal.acctId());
                if (current == null) {
                    throw new IntCalcException("ACCOUNT NOT FOUND: " + bal.acctId());
                }
                currentXref = xrefByAcct.get(bal.acctId());
                if (currentXref == null) {
                    throw new IntCalcException("ACCOUNT NOT FOUND IN XREF: " + bal.acctId());
                }
            }

            BigDecimal rate = rates.rateFor(current.groupId(), bal.typeCode(), bal.catCode());
            if (rate.signum() != 0) {
                BigDecimal monthly = InterestCalculator.monthlyInterest(bal.balance(), rate);
                totalInterest = CobolNumeric.fitPic(totalInterest.add(monthly), 9, 2);
                tranIdSuffix = (tranIdSuffix + 1) % TRANID_SUFFIX_MODULUS;
                transactions.add(interestTransaction(current, currentXref, monthly, tranIdSuffix, timestamp));
                // 1400-COMPUTE-FEES is an unimplemented stub in CBACT04C; intentionally not ported.
            }
        }
        if (current != null && config.updateLastAccount()) {
            current.applyInterest(totalInterest);
        }

        log.printf("RECORDS READ: %d, INTEREST TRANSACTIONS WRITTEN: %d%n",
                ordered.size(), transactions.size());
        log.println("END OF EXECUTION OF PROGRAM CBACT04C (Java)");
        return new Result(transactions, new ArrayList<>(accountFile.values()), ordered.size());
    }

    /** Paragraph 1300-B-WRITE-TX. */
    private Transaction interestTransaction(Account account, CardXref xref, BigDecimal amount,
                                            int suffix, String timestamp) {
        String tranId = FixedWidth.alpha(config.parmDate(), 10) + CobolNumeric.formatUnsigned(suffix, 6);
        return new Transaction(
                tranId,
                INTEREST_TYPE_CD,
                INTEREST_CAT_CD,
                SOURCE,
                DESCRIPTION_PREFIX + account.acctId(),
                amount,
                0L,
                "",
                "",
                "",
                xref.cardNum(),
                timestamp,
                timestamp);
    }

    /**
     * READ XREF-FILE KEY IS FD-XREF-ACCT-ID returns the first record for a duplicate alternate
     * key; for an AIX built from the KSDS that is the lowest card number.
     */
    private static Map<String, CardXref> firstCardPerAccount(List<CardXref> xrefs) {
        Map<String, CardXref> result = new HashMap<>();
        for (CardXref x : xrefs) {
            result.merge(x.acctId(), x, (a, b) -> a.cardNum().compareTo(b.cardNum()) <= 0 ? a : b);
        }
        return result;
    }
}
