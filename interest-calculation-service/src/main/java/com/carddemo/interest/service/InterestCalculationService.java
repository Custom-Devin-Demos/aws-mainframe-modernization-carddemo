package com.carddemo.interest.service;

import com.carddemo.interest.model.Account;
import com.carddemo.interest.model.CardCrossReference;
import com.carddemo.interest.model.DisclosureGroup;
import com.carddemo.interest.model.Transaction;
import com.carddemo.interest.model.TransactionCategoryBalance;
import com.carddemo.interest.repository.AccountRepository;
import com.carddemo.interest.repository.CardCrossReferenceRepository;
import com.carddemo.interest.repository.DisclosureGroupRepository;
import com.carddemo.interest.repository.TransactionCategoryBalanceRepository;
import com.carddemo.interest.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Java/Spring reimplementation of the COBOL batch program
 * {@code app/cbl/CBACT04C.cbl} (CardDemo interest calculator).
 *
 * <p>The original program reads the transaction-category-balance file
 * sequentially, groups records by account, resolves the applicable annual
 * interest rate from the disclosure group table (falling back to a DEFAULT
 * group), computes monthly interest per category, writes an interest
 * transaction for each, and finally posts the accumulated interest back to the
 * account while zeroing the current-cycle credit/debit fields.
 *
 * <p>See {@code interest-calculation-service/TRACEABILITY.md} for the full
 * COBOL paragraph &rarr; Java method mapping.
 */
@Service
public class InterestCalculationService {

    private static final Logger log = LoggerFactory.getLogger(InterestCalculationService.class);

    /** TRAN-TYPE-CD value moved at CBACT04C.cbl line 482. */
    static final String INTEREST_TRAN_TYPE = "01";
    /** TRAN-CAT-CD value moved at CBACT04C.cbl line 483. */
    static final int INTEREST_TRAN_CAT = 5;
    /** TRAN-SOURCE value moved at CBACT04C.cbl line 484. */
    static final String INTEREST_SOURCE = "System";
    /** Group id substituted at CBACT04C.cbl line 437 when status '23' occurs. */
    static final String DEFAULT_GROUP = "DEFAULT";

    /** Divisor in COBOL formula {@code (TRAN-CAT-BAL * DIS-INT-RATE) / 1200}. */
    private static final BigDecimal MONTHS_PER_YEAR_TIMES_100 = BigDecimal.valueOf(1200);

    /** DB2 timestamp layout produced by Z-GET-DB2-FORMAT-TIMESTAMP (X(26)). */
    private static final DateTimeFormatter DB2_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS");
    private static final DateTimeFormatter TRAN_ID_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TransactionCategoryBalanceRepository categoryBalanceRepository;
    private final AccountRepository accountRepository;
    private final DisclosureGroupRepository disclosureGroupRepository;
    private final CardCrossReferenceRepository cardCrossReferenceRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Monotonic transaction-id suffix for a run, mirroring the program-global
     * COBOL counter {@code WS-TRANID-SUFFIX} (declared at CBACT04C.cbl line 173,
     * incremented at line 474). It is reset at the start of every run.
     */
    private long tranIdSuffix;

    public InterestCalculationService(
            TransactionCategoryBalanceRepository categoryBalanceRepository,
            AccountRepository accountRepository,
            DisclosureGroupRepository disclosureGroupRepository,
            CardCrossReferenceRepository cardCrossReferenceRepository,
            TransactionRepository transactionRepository) {
        this.categoryBalanceRepository = categoryBalanceRepository;
        this.accountRepository = accountRepository;
        this.disclosureGroupRepository = disclosureGroupRepository;
        this.cardCrossReferenceRepository = cardCrossReferenceRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Main driver — reimplements the {@code PERFORM UNTIL END-OF-FILE} loop of
     * CBACT04C.cbl (lines 188-222).
     *
     * <p>Reads every transaction-category-balance row in account-id order
     * (mirroring the sequential read of the TCATBALF file), groups them by
     * account, processes each account, and posts the accumulated interest.
     *
     * @param processingDate the run date supplied to the COBOL program via
     *                        {@code PARM-DATE}; used to build transaction ids
     * @return counts of accounts and interest transactions produced
     */
    @Transactional
    public InterestRunResult calculateInterestForAllAccounts(LocalDate processingDate) {
        log.info("START OF EXECUTION OF PROGRAM CBACT04C (Java) for {}", processingDate);
        this.tranIdSuffix = 0L;

        List<TransactionCategoryBalance> balances =
                categoryBalanceRepository.findAllByOrderByAccountId();

        Map<Long, List<TransactionCategoryBalance>> byAccount = new LinkedHashMap<>();
        for (TransactionCategoryBalance balance : balances) {
            byAccount.computeIfAbsent(balance.getAccountId(), k -> new ArrayList<>()).add(balance);
        }

        int accountCount = 0;
        int transactionCount = 0;
        for (Map.Entry<Long, List<TransactionCategoryBalance>> entry : byAccount.entrySet()) {
            transactionCount += processAccount(entry.getKey(), entry.getValue(), processingDate);
            accountCount++;
        }

        log.info("END OF EXECUTION OF PROGRAM CBACT04C (Java): {} accounts, {} transactions",
                accountCount, transactionCount);
        return new InterestRunResult(accountCount, transactionCount);
    }

    /**
     * Processes every category balance for a single account, then posts the
     * accumulated interest back to the account.
     *
     * <p>Combines the per-record body of the main loop (CBACT04C.cbl lines
     * 194-217) with {@code 1100-GET-ACCT-DATA} (372-391),
     * {@code 1110-GET-XREF-DATA} (393-413) and {@code 1050-UPDATE-ACCOUNT}
     * (350-356).
     *
     * @return the number of interest transactions written for this account
     */
    @Transactional
    public int processAccount(Long accountId, List<TransactionCategoryBalance> balances,
                              LocalDate processingDate) {
        // 1100-GET-ACCT-DATA: read the account master record.
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            // COBOL displays 'ACCOUNT NOT FOUND' and abends; here we skip the
            // orphaned balance group so the rest of the run can complete.
            log.warn("ACCOUNT NOT FOUND: {}", accountId);
            return 0;
        }
        Account account = accountOpt.get();

        // 1110-GET-XREF-DATA: resolve the card number for the interest tx.
        String cardNumber = cardCrossReferenceRepository.findByAccountId(accountId)
                .map(CardCrossReference::getCardNumber)
                .orElse(null);

        String groupId = trim(account.getGroupId());

        BigDecimal totalInterest = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<Transaction> newTransactions = new ArrayList<>();

        for (TransactionCategoryBalance balance : balances) {
            BigDecimal rate =
                    resolveInterestRate(groupId, balance.getTypeCode(), balance.getCategoryCode());
            // CBACT04C.cbl line 214: IF DIS-INT-RATE NOT = 0.
            if (rate.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal monthlyInterest = computeMonthlyInterest(balance.getBalance(), rate);
                totalInterest = totalInterest.add(monthlyInterest);
                newTransactions.add(buildInterestTransaction(
                        accountId, cardNumber, monthlyInterest, processingDate, ++tranIdSuffix));
            }
        }

        // 1050-UPDATE-ACCOUNT (lines 350-356).
        BigDecimal currentBalance = account.getCurrentBalance() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : account.getCurrentBalance();
        account.setCurrentBalance(currentBalance.add(totalInterest));
        account.setCycleCredit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        account.setCycleDebit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        accountRepository.save(account);

        transactionRepository.saveAll(newTransactions);
        return newTransactions.size();
    }

    /**
     * Resolves the annual interest rate for a group / type / category.
     *
     * <p>Reimplements {@code 1200-GET-INTEREST-RATE} (lines 415-440): it first
     * reads the account's own disclosure group; on a record-not-found
     * (COBOL file status '23', line 436) it retries with the {@code DEFAULT}
     * group via {@code 1200-A-GET-DEFAULT-INT-RATE} (lines 443-460). If neither
     * is present it returns {@link BigDecimal#ZERO}.
     */
    public BigDecimal resolveInterestRate(String groupId, String typeCode, int categoryCode) {
        Optional<DisclosureGroup> primary = disclosureGroupRepository
                .findByGroupIdAndTypeCodeAndCategoryCode(groupId, typeCode, categoryCode);
        if (primary.isPresent()) {
            return nullToZero(primary.get().getInterestRate());
        }

        Optional<DisclosureGroup> fallback = disclosureGroupRepository
                .findByGroupIdAndTypeCodeAndCategoryCode(DEFAULT_GROUP, typeCode, categoryCode);
        return fallback.map(g -> nullToZero(g.getInterestRate())).orElse(BigDecimal.ZERO);
    }

    /**
     * Computes one month of interest for a single category balance.
     *
     * <p>Reimplements {@code 1300-COMPUTE-INTEREST} (lines 462-470):
     * {@code COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200}.
     * The result is scaled to 2 decimals using {@link RoundingMode#HALF_UP}.
     *
     * @param categoryBalance   the category balance (TRAN-CAT-BAL)
     * @param annualRatePercent the annual interest rate as a percentage
     *                          (DIS-INT-RATE), e.g. {@code 18.00} for 18%
     */
    public BigDecimal computeMonthlyInterest(BigDecimal categoryBalance,
                                             BigDecimal annualRatePercent) {
        if (categoryBalance == null || annualRatePercent == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return categoryBalance
                .multiply(annualRatePercent)
                .divide(MONTHS_PER_YEAR_TIMES_100, 2, RoundingMode.HALF_UP);
    }

    /**
     * Builds (but does not persist) the interest transaction for one category.
     *
     * <p>Reimplements {@code 1300-B-WRITE-TX} (lines 473-515). Field mappings:
     * type code {@code "01"}, category code {@code 5}, source {@code "System"},
     * description {@code "Int. for a/c " + accountId}, amount = the monthly
     * interest, merchant id {@code 0} and blank merchant fields, card number
     * from the XREF lookup, and origin/process timestamps set to "now".
     *
     * <p>The transaction id is the processing date formatted as
     * {@code yyyyMMdd} followed by the zero-padded run sequence number (mirrors
     * {@code STRING PARM-DATE, WS-TRANID-SUFFIX} at lines 476-480).
     */
    public Transaction buildInterestTransaction(Long accountId, String cardNumber,
                                                BigDecimal monthlyInterest,
                                                LocalDate processingDate, long sequenceNumber) {
        String timestamp = LocalDateTime.now().format(DB2_TS);
        String transactionId = processingDate.format(TRAN_ID_DATE)
                + String.format("%08d", sequenceNumber);

        return Transaction.builder()
                .transactionId(transactionId)
                .typeCode(INTEREST_TRAN_TYPE)
                .categoryCode(INTEREST_TRAN_CAT)
                .source(INTEREST_SOURCE)
                .description("Int. for a/c " + accountId)
                .amount(monthlyInterest)
                .merchantId(0L)
                .merchantName("")
                .merchantCity("")
                .merchantZip("")
                .cardNumber(cardNumber)
                .originTimestamp(timestamp)
                .processTimestamp(timestamp)
                .build();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
