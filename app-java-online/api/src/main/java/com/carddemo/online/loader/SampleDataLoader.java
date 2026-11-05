package com.carddemo.online.loader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports the CardDemo sample data files (app/data/ASCII or app/data/EBCDIC) into PostgreSQL.
 * Runs at startup when {@code carddemo.loader.enabled=true}; each table is only loaded when empty.
 */
@Component
public class SampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    /** Every table populated by the loader. */
    private static final List<String> TABLES = List.of("user_security", "customer", "account", "card", "card_xref",
            "tran_type", "tran_category", "tran_cat_balance", "disclosure_group", "transaction");

    private final JdbcTemplate jdbc;
    private final LoaderProperties props;
    private final PasswordEncoder passwordEncoder;

    public SampleDataLoader(JdbcTemplate jdbc, LoaderProperties props, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.enabled()) {
            Map<String, Integer> counts = loadAll();
            log.info("CardDemo sample data loaded from {} ({}): {}", props.dataDir(), props.encoding(), counts);
        }
    }

    /** Loads every empty table and returns the number of rows inserted per table. */
    @Transactional
    public Map<String, Integer> loadAll() {
        DatasetReader reader = new DatasetReader(Path.of(props.dataDir()),
                props.encoding() == null ? DataEncoding.ASCII : props.encoding());
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("user_security", load(reader, Dataset.USRSEC, "user_security",
                "INSERT INTO user_security (usr_id, first_name, last_name, password_hash, usr_type) VALUES (?,?,?,?,?)",
                r -> new Object[] {r.text(1, 8), r.text(9, 20), r.text(29, 20), passwordEncoder.encode(r.text(49, 8)),
                        r.text(57, 1)}));
        counts.put("customer", load(reader, Dataset.CUSTDATA, "customer",
                "INSERT INTO customer (cust_id, first_name, middle_name, last_name, addr_line_1, addr_line_2,"
                        + " addr_line_3, addr_state_cd, addr_country_cd, addr_zip, phone_num_1, phone_num_2, ssn,"
                        + " govt_issued_id, dob, eft_account_id, pri_card_holder_ind, fico_credit_score)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                r -> new Object[] {r.number(1, 9), r.text(10, 25), r.text(35, 25), r.text(60, 25), r.text(85, 50),
                        r.text(135, 50), r.text(185, 50), r.text(235, 2), r.text(237, 3), r.text(240, 10),
                        r.text(250, 15), r.text(265, 15), r.text(280, 9), r.text(289, 20), r.date(309),
                        r.text(319, 10), r.text(329, 1), (int) r.number(330, 3)}));
        counts.put("account", load(reader, Dataset.ACCTDATA, "account",
                "INSERT INTO account (acct_id, active_status, curr_bal, credit_limit, cash_credit_limit, open_date,"
                        + " expiration_date, reissue_date, curr_cyc_credit, curr_cyc_debit, addr_zip, group_id)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                r -> new Object[] {r.number(1, 11), r.text(12, 1), r.decimal(13, 12, 2), r.decimal(25, 12, 2),
                        r.decimal(37, 12, 2), r.date(49), r.date(59), r.date(69), r.decimal(79, 12, 2),
                        r.decimal(91, 12, 2), r.text(103, 10), r.text(113, 10)}));
        counts.put("card", load(reader, Dataset.CARDDATA, "card",
                "INSERT INTO card (card_num, acct_id, cvv_cd, embossed_name, expiration_date, active_status)"
                        + " VALUES (?,?,?,?,?,?)",
                r -> new Object[] {r.text(1, 16), r.number(17, 11), (int) r.number(28, 3), r.text(31, 50), r.date(81),
                        r.text(91, 1)}));
        counts.put("card_xref", load(reader, Dataset.CARDXREF, "card_xref",
                "INSERT INTO card_xref (card_num, cust_id, acct_id) VALUES (?,?,?)",
                r -> new Object[] {r.text(1, 16), r.number(17, 9), r.number(26, 11)}));
        counts.put("tran_type", load(reader, Dataset.TRANTYPE, "tran_type",
                "INSERT INTO tran_type (tran_type, description) VALUES (?,?)",
                r -> new Object[] {r.text(1, 2), r.text(3, 50)}));
        counts.put("tran_category", load(reader, Dataset.TRANCATG, "tran_category",
                "INSERT INTO tran_category (tran_type_cd, tran_cat_cd, description) VALUES (?,?,?)",
                r -> new Object[] {r.text(1, 2), (int) r.number(3, 4), r.text(7, 50)}));
        counts.put("tran_cat_balance", load(reader, Dataset.TCATBALF, "tran_cat_balance",
                "INSERT INTO tran_cat_balance (acct_id, tran_type_cd, tran_cat_cd, balance) VALUES (?,?,?,?)",
                r -> new Object[] {r.number(1, 11), r.text(12, 2), (int) r.number(14, 4), r.decimal(18, 11, 2)}));
        counts.put("disclosure_group", load(reader, Dataset.DISCGRP, "disclosure_group",
                "INSERT INTO disclosure_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES (?,?,?,?)",
                r -> new Object[] {r.text(1, 10), r.text(11, 2), (int) r.number(13, 4), r.decimal(17, 6, 2)}));
        counts.put("transaction", load(reader, Dataset.DALYTRAN, "transaction",
                "INSERT INTO transaction (tran_id, tran_type_cd, tran_cat_cd, source, description, amount,"
                        + " merchant_id, merchant_name, merchant_city, merchant_zip, card_num, orig_ts, proc_ts)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                r -> new Object[] {r.text(1, 16), r.text(17, 2), (int) r.number(19, 4), r.text(23, 10),
                        r.text(33, 100), r.decimal(133, 11, 2), r.number(144, 9), r.text(153, 50), r.text(203, 50),
                        r.text(253, 10), r.text(263, 16), r.timestamp(279), r.timestamp(305)}));
        return counts;
    }

    /** Deletes all rows from every CardDemo table (used by tests to reset state before reloading). */
    @Transactional
    public void truncateAll() {
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", TABLES));
    }

    private int load(DatasetReader reader, Dataset dataset, String table, String sql,
            Function<FixedWidthRecord, Object[]> mapper) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        if (existing != null && existing > 0) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<>();
        for (FixedWidthRecord r : reader.read(dataset)) {
            Object[] row = mapper.apply(r);
            if (row[0] instanceof String key && key.isBlank()) {
                continue;
            }
            rows.add(row);
        }
        jdbc.batchUpdate(sql, rows);
        return rows.size();
    }
}
