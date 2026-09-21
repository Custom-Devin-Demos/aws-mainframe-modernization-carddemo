package com.carddemo.interest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.interest.domain.Account;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountPostingServiceTest {

    private final AccountPostingService service = new AccountPostingService();

    private static Account account(String bal, String cycCredit, String cycDebit) {
        return new Account("00000000010", "Y", new BigDecimal(bal), new BigDecimal("5000.00"),
                new BigDecimal("1000.00"), "2020-01-01", "2025-01-01", "2023-01-01",
                new BigDecimal(cycCredit), new BigDecimal(cycDebit), "12345", "A000000001");
    }

    @Test
    void paragraph1050UpdateAccount_addsTotalInterestToCurrBalAndZeroesCycleFields() {
        Account updated = service.postInterest(account("1000.00", "250.00", "75.50"), new BigDecimal("12.34"));

        assertThat(updated.currBal()).isEqualTo(new BigDecimal("1012.34"));
        assertThat(updated.currCycCredit()).isEqualTo(new BigDecimal("0.00"));
        assertThat(updated.currCycDebit()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void paragraph1050UpdateAccount_negativeInterestReducesBalance() {
        Account updated = service.postInterest(account("-100.00", "0.00", "0.00"), new BigDecimal("-1.25"));
        assertThat(updated.currBal()).isEqualTo(new BigDecimal("-101.25"));
    }

    @Test
    void paragraph1050UpdateAccount_zeroTotalStillZeroesCycleFieldsAndKeepsOtherFields() {
        Account original = account("1000.00", "9.99", "8.88");
        Account updated = service.postInterest(original, new BigDecimal("0.00"));

        assertThat(updated.currBal()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(updated.currCycCredit()).isEqualTo(new BigDecimal("0.00"));
        assertThat(updated.currCycDebit()).isEqualTo(new BigDecimal("0.00"));
        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.creditLimit()).isEqualTo(original.creditLimit());
        assertThat(updated.groupId()).isEqualTo(original.groupId());
    }

    @Test
    void paragraph1400ComputeFees_isANoOp() {
        Account original = account("1000.00", "1.00", "2.00");
        service.computeFees(original);
        assertThat(original).isEqualTo(account("1000.00", "1.00", "2.00"));
    }
}
