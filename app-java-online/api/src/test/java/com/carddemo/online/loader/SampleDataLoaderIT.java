package com.carddemo.online.loader;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.online.domain.Account;
import com.carddemo.online.domain.AccountRepository;
import com.carddemo.online.domain.CardXrefRepository;
import com.carddemo.online.domain.Customer;
import com.carddemo.online.domain.CustomerRepository;
import com.carddemo.online.domain.Transaction;
import com.carddemo.online.domain.TransactionRepository;
import com.carddemo.online.domain.UserSecurity;
import com.carddemo.online.domain.UserSecurityRepository;
import com.carddemo.online.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class SampleDataLoaderIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    UserSecurityRepository users;
    @Autowired
    AccountRepository accounts;
    @Autowired
    CustomerRepository customers;
    @Autowired
    CardXrefRepository xrefs;
    @Autowired
    TransactionRepository transactions;

    @Test
    void loadsEveryTableFromSampleData() {
        resetSampleData();
        Map<String, Integer> expected = Map.of("user_security", 10, "customer", 50, "account", 50, "card", 50,
                "card_xref", 50, "tran_type", 7, "tran_category", 18, "tran_cat_balance", 50,
                "disclosure_group", 51, "transaction", 300);
        expected.forEach((table, rows) ->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class))
                        .as(table).isEqualTo(rows));
    }

    @Test
    void decodesEbcdicUserSecurityFile() {
        UserSecurity admin = users.findById("ADMIN001").orElseThrow();
        assertThat(admin.getFirstName()).isEqualTo("MARGARET");
        assertThat(admin.getLastName()).isEqualTo("GOLD");
        assertThat(passwordEncoder.matches("PASSWORD", admin.getPasswordHash())).isTrue();
        assertThat(admin.getUsrType()).isEqualTo("A");
        assertThat(users.findById("USER0001").orElseThrow().getUsrType()).isEqualTo("U");
    }

    @Test
    void decodesZonedDecimalsAndDates() {
        Account a = accounts.findById(1L).orElseThrow();
        assertThat(a.getActiveStatus()).isEqualTo("Y");
        assertThat(a.getCurrBal()).isEqualByComparingTo(new BigDecimal("194.00"));
        assertThat(a.getCreditLimit()).isEqualByComparingTo(new BigDecimal("2020.00"));
        assertThat(a.getCashCreditLimit()).isEqualByComparingTo(new BigDecimal("1020.00"));
        assertThat(a.getOpenDate()).isEqualTo(LocalDate.of(2014, 11, 20));
        // The sample file puts the group code in the ACCT-ADDR-ZIP slot; loaded per CVACT01Y offsets.
        assertThat(a.getAddrZip()).isEqualTo("A000000000");
        assertThat(a.getGroupId()).isEmpty();

        Customer c = customers.findById(1L).orElseThrow();
        assertThat(c.getFirstName()).isEqualTo("Immanuel");
        assertThat(c.getAddrStateCd()).isEqualTo("NC");
        assertThat(c.getAddrCountryCd()).isEqualTo("USA");
        assertThat(c.getSsn()).isEqualTo("020973888");
        assertThat(c.getDob()).isEqualTo(LocalDate.of(1961, 6, 8));
        assertThat(c.getFicoCreditScore()).isEqualTo(274);

        assertThat(xrefs.findFirstByAcctIdOrderByCardNumAsc(50L)).get()
                .extracting(x -> x.getCardNum()).isEqualTo("0500024453765740");

        Transaction t = transactions.findById("0000000000683580").orElseThrow();
        assertThat(t.getAmount()).isEqualByComparingTo(new BigDecimal("504.77"));
        assertThat(t.getOrigTs()).isNotNull();
    }

    /**
     * The two sample-data encodings agree on every record except these two:
     * account 49's group code (ZEROAPR in EBCDIC, A000000000 in ASCII) and the
     * DEFAULT/07/0001 disclosure-group interest rate (0.00 in ASCII, 1.50 in EBCDIC).
     */
    private static final java.util.Set<String> KNOWN_ENCODING_DIFFERENCES = java.util.Set.of("ACCTDATA#48", "DISCGRP#33");

    @Test
    void asciiAndEbcdicFilesDecodeToTheSameRecords() {
        DatasetReader reader = new DatasetReader(java.nio.file.Path.of("../../app/data"), DataEncoding.ASCII);
        java.util.Set<String> differences = new java.util.TreeSet<>();
        for (Dataset d : Dataset.values()) {
            if (d.asciiFile() == null) {
                continue;
            }
            var ascii = reader.read(d, DataEncoding.ASCII);
            var ebcdic = reader.read(d, DataEncoding.EBCDIC);
            assertThat(ebcdic).as(d.name()).hasSameSizeAs(ascii);
            for (int i = 0; i < ascii.size(); i++) {
                if (!ebcdic.get(i).text(1, d.recordLength()).equals(ascii.get(i).text(1, d.recordLength()))) {
                    differences.add(d.name() + "#" + i);
                }
            }
        }
        assertThat(differences).isEqualTo(KNOWN_ENCODING_DIFFERENCES);
    }
}
