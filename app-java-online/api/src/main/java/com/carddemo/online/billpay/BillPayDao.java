package com.carddemo.online.billpay;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The VSAM file operations of COBIL00C (ACCTDAT, CXACAIX, TRANSACT) as SQL. */
@Repository
class BillPayDao {

    private final JdbcTemplate jdbc;

    BillPayDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** READ ACCTDAT [UPDATE]: the current balance, row-locked when {@code forUpdate}. */
    Optional<BigDecimal> readBalance(long acctId, boolean forUpdate) {
        List<BigDecimal> rows = jdbc.queryForList(
                "SELECT curr_bal FROM account WHERE acct_id = ?" + (forUpdate ? " FOR UPDATE" : ""),
                BigDecimal.class, acctId);
        return rows.stream().findFirst();
    }

    /** READ CXACAIX: first card cross-reference for the account (alternate index on XREF-ACCT-ID). */
    Optional<String> readXrefCardNum(long acctId) {
        List<String> rows = jdbc.queryForList(
                "SELECT card_num FROM card_xref WHERE acct_id = ? ORDER BY card_num LIMIT 1", String.class, acctId);
        return rows.stream().findFirst();
    }

    /** STARTBR HIGH-VALUES + READPREV on TRANSACT: the highest TRAN-ID, empty when the file has no records. */
    Optional<String> readLastTranId() {
        return Optional.ofNullable(jdbc.queryForObject("SELECT MAX(tran_id) FROM transaction", String.class));
    }

    /** WRITE TRANSACT. */
    void insertTransaction(String tranId, String cardNum, BigDecimal amount, LocalDateTime timestamp) {
        jdbc.update("INSERT INTO transaction (tran_id, tran_type_cd, tran_cat_cd, source, description, amount,"
                + " merchant_id, merchant_name, merchant_city, merchant_zip, card_num, orig_ts, proc_ts)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                tranId, BillPayService.TRAN_TYPE_CD, BillPayService.TRAN_CAT_CD, BillPayService.TRAN_SOURCE,
                BillPayService.TRAN_DESC, amount, BillPayService.MERCHANT_ID, BillPayService.MERCHANT_NAME,
                BillPayService.MERCHANT_CITY, BillPayService.MERCHANT_ZIP, cardNum, timestamp, timestamp);
    }

    /** REWRITE ACCTDAT; returns the number of rows updated (0 = NOTFND). */
    int updateBalance(long acctId, BigDecimal newBalance) {
        return jdbc.update("UPDATE account SET curr_bal = ? WHERE acct_id = ?", newBalance, acctId);
    }
}
