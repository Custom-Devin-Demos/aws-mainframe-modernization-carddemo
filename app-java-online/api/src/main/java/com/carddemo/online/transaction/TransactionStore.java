package com.carddemo.online.transaction;

import com.carddemo.online.domain.CardXref;
import com.carddemo.online.domain.CardXrefRepository;
import com.carddemo.online.domain.Transaction;
import com.carddemo.online.domain.TransactionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * TRANSACT and CCXREF/CXACAIX file access used by COTRN00C/01C/02C. Browse queries compare keys with
 * {@code COLLATE "C"} so that STARTBR GTEQ / READNEXT / READPREV follow the byte order of the VSAM key.
 */
@Component
public class TransactionStore {

    private static final String COLUMNS = "tran_id, tran_type_cd, tran_cat_cd, source, description, amount,"
            + " merchant_id, merchant_name, merchant_city, merchant_zip, card_num, orig_ts, proc_ts";

    private final JdbcTemplate jdbc;
    private final TransactionRepository transactions;
    private final CardXrefRepository xrefs;

    public TransactionStore(JdbcTemplate jdbc, TransactionRepository transactions, CardXrefRepository xrefs) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.xrefs = xrefs;
    }

    /** STARTBR GTEQ {@code key} followed by READNEXTs: records with TRAN-ID >= key, ascending. */
    public List<Transaction> readFrom(String key, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM transaction WHERE tran_id COLLATE \"C\" >= ?"
                + " ORDER BY tran_id COLLATE \"C\" LIMIT ?", TransactionStore::map, key, limit);
    }

    /** READPREVs from just before {@code key}: records with TRAN-ID < key, descending. */
    public List<Transaction> readBefore(String key, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM transaction WHERE tran_id COLLATE \"C\" < ?"
                + " ORDER BY tran_id COLLATE \"C\" DESC LIMIT ?", TransactionStore::map, key, limit);
    }

    /** True if any record has a TRAN-ID greater than {@code key} (the NEXT-PAGE-FLG lookahead). */
    public boolean existsAfter(String key) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM transaction WHERE tran_id COLLATE \"C\" > ?)", Boolean.class, key);
        return Boolean.TRUE.equals(exists);
    }

    /** READ TRANSACT by exact key. */
    public Optional<Transaction> find(String tranId) {
        return transactions.findById(tranId);
    }

    /** STARTBR HIGH-VALUES + READPREV: the record with the highest TRAN-ID. */
    public Optional<Transaction> findLast() {
        return transactions.findTopByOrderByTranIdDesc();
    }

    /** READ CXACAIX (alternate index on XREF-ACCT-ID): first xref of the account. */
    public Optional<CardXref> xrefByAccount(long acctId) {
        return xrefs.findFirstByAcctIdOrderByCardNumAsc(acctId);
    }

    /** READ CCXREF by card number. */
    public Optional<CardXref> xrefByCard(String cardNum) {
        return xrefs.findById(cardNum);
    }

    /** WRITE TRANSACT; a plain INSERT so that an existing key raises DuplicateKeyException (DUPREC). */
    public void insert(Transaction t) {
        jdbc.update("INSERT INTO transaction (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                t.getTranId(), t.getTranTypeCd(), t.getTranCatCd(), t.getSource(), t.getDescription(),
                t.getAmount(), t.getMerchantId(), t.getMerchantName(), t.getMerchantCity(), t.getMerchantZip(),
                t.getCardNum(), toTimestamp(t.getOrigTs()), toTimestamp(t.getProcTs()));
    }

    private static Timestamp toTimestamp(java.time.LocalDateTime ts) {
        return ts == null ? null : Timestamp.valueOf(ts);
    }

    private static Transaction map(ResultSet rs, int rowNum) throws SQLException {
        Transaction t = new Transaction();
        t.setTranId(rs.getString("tran_id"));
        t.setTranTypeCd(rs.getString("tran_type_cd"));
        t.setTranCatCd(rs.getInt("tran_cat_cd"));
        t.setSource(rs.getString("source"));
        t.setDescription(rs.getString("description"));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setMerchantId(rs.getLong("merchant_id"));
        t.setMerchantName(rs.getString("merchant_name"));
        t.setMerchantCity(rs.getString("merchant_city"));
        t.setMerchantZip(rs.getString("merchant_zip"));
        t.setCardNum(rs.getString("card_num"));
        Timestamp orig = rs.getTimestamp("orig_ts");
        Timestamp proc = rs.getTimestamp("proc_ts");
        t.setOrigTs(orig == null ? null : orig.toLocalDateTime());
        t.setProcTs(proc == null ? null : proc.toLocalDateTime());
        return t;
    }
}
