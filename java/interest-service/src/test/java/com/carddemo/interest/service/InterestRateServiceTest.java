package com.carddemo.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.interest.domain.DisclosureGroup;
import com.carddemo.interest.repository.InMemoryDisclosureGroupRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterestRateServiceTest {

    private final InMemoryDisclosureGroupRepository repo = new InMemoryDisclosureGroupRepository();
    private final InterestRateService service = new InterestRateService(repo);

    @BeforeEach
    void loadDiscgrp() {
        repo.load(List.of(
                new DisclosureGroup("A000000001", "01", "0001", new BigDecimal("12.50")),
                new DisclosureGroup("DEFAULT", "01", "0001", new BigDecimal("1.00")),
                new DisclosureGroup("DEFAULT", "02", "0002", new BigDecimal("0.00"))));
    }

    @Test
    void paragraph1200GetInterestRate_returnsRateForAccountGroup() {
        assertThat(service.getRate("A000000001", "01", "0001")).isEqualByComparingTo("12.50");
    }

    @Test
    void paragraph1200AGetDefaultIntRate_fallsBackToDefaultGroupWhenGroupMissing() {
        assertThat(service.getRate("ZEROAPR", "01", "0001")).isEqualByComparingTo("1.00");
    }

    @Test
    void paragraph1200AGetDefaultIntRate_returnsZeroRateSoCallerSkipsInterest() {
        BigDecimal rate = service.getRate("NOGROUP", "02", "0002");
        assertThat(rate.signum()).isZero();
        assertThat(rate.scale()).isEqualTo(2);
    }

    @Test
    void paragraph1200AGetDefaultIntRate_abendsWhenNeitherGroupNorDefaultExists() {
        assertThatThrownBy(() -> service.getRate("A000000001", "99", "9999"))
                .isInstanceOf(InterestServiceException.class)
                .hasMessageContaining("DEFAULT");
    }

    @Test
    void paragraph1200GetInterestRate_doesNotFallBackWhenGroupMatchesOnDifferentCategory() {
        // group exists for 01/0001 only; 02/0002 must come from DEFAULT
        assertThat(service.getRate("A000000001", "02", "0002")).isEqualByComparingTo("0.00");
    }
}
