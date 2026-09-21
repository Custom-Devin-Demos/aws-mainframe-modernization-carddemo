package com.carddemo.interest.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.DisclosureGroup;
import com.carddemo.interest.domain.TranCatBalance;
import com.carddemo.interest.domain.Transaction;
import com.carddemo.interest.repository.InMemoryDisclosureGroupRepository;
import com.carddemo.interest.service.InterestRateService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Drives reader -> InterestProcessor -> writer over the repository sample data
 * (app/data/ASCII, the same files JCL INTCALC reads) and checks the CBACT04C invariants:
 * one interest transaction per TCATBAL record whose rate is non-zero, sequential TRAN-IDs,
 * every posted account rewritten with cycle credit/debit zeroed and interest added. The final
 * control group is posted only with {@code postFinalAccount=true} (see InterestJobConfig).
 */
@SpringBootTest
@SpringBatchTest
class InterestJobIntegrationTest {

    static final Path ASCII_DATA = Path.of("..", "..", "app", "data", "ASCII");
    static final String RUN_DATE = "2022071800";

    @Autowired
    JobLauncherTestUtils jobLauncherTestUtils;

    @TempDir
    Path work;

    @ParameterizedTest(name = "postFinalAccount={0}")
    @ValueSource(booleans = {false, true})
    void procedureDivision_mainLoop_oneTransactionPerNonZeroRateCategoryAndAccountsRewritten(boolean postFinalAccount)
            throws Exception {
        Path tcatbal = copy("tcatbal.txt");
        Path discgrp = copy("discgrp.txt");
        Path xref = copy("cardxref.txt");
        Path acct = copy("acctdata.txt");
        Path tran = work.resolve("systran.txt");
        List<Account> accountsBefore = read(acct, Account::fromRecord);

        JobExecution execution = jobLauncherTestUtils.launchJob(
                params(tcatbal, xref, discgrp, acct, tran, postFinalAccount));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<TranCatBalance> balances = read(tcatbal, TranCatBalance::fromRecord);
        Map<String, Account> accountsById = new LinkedHashMap<>();
        accountsBefore.forEach(a -> accountsById.put(a.id(), a));
        InMemoryDisclosureGroupRepository groups = new InMemoryDisclosureGroupRepository();
        groups.load(read(discgrp, DisclosureGroup::fromRecord));
        InterestRateService rates = new InterestRateService(groups);

        Map<String, BigDecimal> expectedInterest = new LinkedHashMap<>();
        long expectedTransactions = 0;
        for (TranCatBalance b : balances) {
            BigDecimal rate = rates.getRate(accountsById.get(b.acctId()).groupId(), b.typeCd(), b.catCd());
            if (rate.signum() != 0) {
                expectedTransactions++;
                BigDecimal monthly = b.balance().multiply(rate).divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.DOWN);
                expectedInterest.merge(b.acctId(), monthly, BigDecimal::add);
            }
        }
        assertThat(expectedTransactions).isPositive();

        List<String> tranLines = Files.readAllLines(tran, StandardCharsets.UTF_8);
        assertThat(tranLines).hasSize((int) expectedTransactions);
        assertThat(tranLines).allSatisfy(line -> assertThat(line).hasSize(Transaction.RECORD_LENGTH));
        List<Transaction> transactions = tranLines.stream().map(Transaction::fromRecord).toList();
        for (int i = 0; i < transactions.size(); i++) {
            Transaction tx = transactions.get(i);
            assertThat(tx.id()).isEqualTo(RUN_DATE + String.format("%06d", i + 1));
            assertThat(tx.typeCd()).isEqualTo("01");
            assertThat(tx.catCd()).isEqualTo("0005");
            assertThat(tx.source()).isEqualTo("System");
            assertThat(tx.description()).startsWith("Int. for a/c ");
            assertThat(tx.origTs()).hasSize(26).isEqualTo(tx.procTs());
        }

        List<String> acctLines = Files.readAllLines(acct, StandardCharsets.UTF_8);
        assertThat(acctLines).hasSize(accountsBefore.size());
        assertThat(acctLines).allSatisfy(line -> assertThat(line).hasSize(Account.RECORD_LENGTH));
        List<Account> accountsAfter = acctLines.stream().map(Account::fromRecord).toList();
        String lastAcctId = balances.get(balances.size() - 1).acctId();
        for (int i = 0; i < accountsAfter.size(); i++) {
            Account before = accountsBefore.get(i);
            Account after = accountsAfter.get(i);
            assertThat(after.id()).isEqualTo(before.id());
            BigDecimal interest = expectedInterest.getOrDefault(before.id(), new BigDecimal("0.00"));
            boolean touched = balances.stream().anyMatch(b -> b.acctId().equals(before.id()))
                    && (postFinalAccount || !before.id().equals(lastAcctId));
            if (touched) {
                assertThat(after.currBal()).isEqualByComparingTo(before.currBal().add(interest));
                assertThat(after.currCycCredit()).isEqualByComparingTo("0.00");
                assertThat(after.currCycDebit()).isEqualByComparingTo("0.00");
            } else {
                assertThat(after).isEqualTo(before);
            }
        }
    }

    static JobParameters params(Path tcatbal, Path xref, Path discgrp, Path acct, Path tran,
                                boolean postFinalAccount) {
        return new JobParametersBuilder()
                .addString(InterestJobConfig.PARAM_POST_FINAL_ACCOUNT, Boolean.toString(postFinalAccount))
                .addString(InterestJobConfig.PARAM_RUN_DATE, RUN_DATE)
                .addString(InterestJobConfig.PARAM_TCATBAL_FILE, tcatbal.toString())
                .addString(InterestJobConfig.PARAM_XREF_FILE, xref.toString())
                .addString(InterestJobConfig.PARAM_DISCGRP_FILE, discgrp.toString())
                .addString(InterestJobConfig.PARAM_ACCT_FILE, acct.toString())
                .addString(InterestJobConfig.PARAM_TRAN_FILE, tran.toString())
                .addLong("ts", System.nanoTime())
                .toJobParameters();
    }

    private Path copy(String name) throws IOException {
        return Files.copy(ASCII_DATA.resolve(name), work.resolve(name));
    }

    static <T> List<T> read(Path file, java.util.function.Function<String, T> parser) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(l -> !l.isEmpty()).map(parser).toList();
    }
}
