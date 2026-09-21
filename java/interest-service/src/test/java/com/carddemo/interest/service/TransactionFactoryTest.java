package com.carddemo.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.CardXref;
import com.carddemo.interest.domain.Transaction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransactionFactoryTest {

    private static final String RUN_DATE = "2022071800";
    private static final String TS = "2022-07-18-10.15.30.120000";

    private final TransactionFactory factory = new TransactionFactory();
    private final Account account = new Account("00000000010", "Y", new BigDecimal("1000.00"),
            new BigDecimal("5000.00"), new BigDecimal("1000.00"), "2020-01-01", "2025-01-01",
            "2023-01-01", new BigDecimal("0.00"), new BigDecimal("0.00"), "12345", "A000000001");
    private final CardXref xref = new CardXref("4111111111111111", "000000001", "00000000010");

    @Test
    void paragraph1300BWriteTx_buildsInterestTransactionFieldByField() {
        Transaction tx = factory.interestTransaction(RUN_DATE, 1, account, xref, new BigDecimal("12.34"), TS);

        assertThat(tx.id()).isEqualTo("2022071800000001");
        assertThat(tx.typeCd()).isEqualTo("01");
        assertThat(tx.catCd()).isEqualTo("0005");
        assertThat(tx.source()).isEqualTo("System");
        assertThat(tx.description()).isEqualTo("Int. for a/c 00000000010");
        assertThat(tx.amount()).isEqualTo(new BigDecimal("12.34"));
        assertThat(tx.merchantId()).isEqualTo("000000000");
        assertThat(tx.merchantName()).isEmpty();
        assertThat(tx.merchantCity()).isEmpty();
        assertThat(tx.merchantZip()).isEmpty();
        assertThat(tx.cardNum()).isEqualTo("4111111111111111");
        assertThat(tx.origTs()).isEqualTo(TS);
        assertThat(tx.procTs()).isEqualTo(TS);
    }

    @Test
    void paragraph1300BWriteTx_tranIdIsParmDatePlusSixDigitSuffix() {
        assertThat(TransactionFactory.transactionId(RUN_DATE, 42)).isEqualTo("2022071800000042");
        assertThat(TransactionFactory.transactionId(RUN_DATE, 999999)).isEqualTo("2022071800999999");
        assertThat(TransactionFactory.transactionId(RUN_DATE, 1)).hasSize(16);
    }

    @Test
    void paragraph1300BWriteTx_suffixWrapsLikePic9of6() {
        assertThat(TransactionFactory.transactionId(RUN_DATE, 1_000_000)).isEqualTo("2022071800000000");
    }

    @Test
    void paragraph1300BWriteTx_shortParmDateIsSpacePaddedToTen() {
        assertThat(TransactionFactory.transactionId("20220718", 7)).isEqualTo("20220718  000007");
    }

    @Test
    void paragraph1300BWriteTx_negativeSuffixRejected() {
        assertThatThrownBy(() -> TransactionFactory.transactionId(RUN_DATE, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paragraph1300BWriteTx_negativeInterestAmountIsPreserved() {
        Transaction tx = factory.interestTransaction(RUN_DATE, 2, account, xref, new BigDecimal("-0.05"), TS);
        assertThat(tx.amount()).isEqualTo(new BigDecimal("-0.05"));
    }
}
