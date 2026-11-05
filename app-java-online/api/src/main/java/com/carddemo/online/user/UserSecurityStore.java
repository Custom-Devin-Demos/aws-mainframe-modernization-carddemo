package com.carddemo.online.user;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * USRSEC file access used by the user-admin programs (STARTBR/READNEXT/READPREV, READ UPDATE, WRITE, REWRITE, DELETE).
 * Browses compare keys bytewise ({@code COLLATE "C"}) like a VSAM KSDS key sequence.
 */
@Repository
public class UserSecurityStore {

    private static final String COLUMNS = "usr_id, first_name, last_name, password_hash, usr_type";
    private static final RowMapper<UserRecord> MAPPER = (rs, n) -> new UserRecord(rs.getString("usr_id"),
            rs.getString("first_name"), rs.getString("last_name"), rs.getString("password_hash"),
            rs.getString("usr_type"));

    private static final String SELECT = "SELECT " + COLUMNS + " FROM user_security WHERE usr_id ";
    private static final String KEY_ORDER = " ORDER BY usr_id COLLATE \"C\"";

    private final JdbcTemplate jdbc;

    public UserSecurityStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** STARTBR GTEQ {@code key} followed by READNEXTs. */
    public List<UserRecord> browseFrom(String key, int limit) {
        return jdbc.query(SELECT + ">= ? COLLATE \"C\"" + KEY_ORDER + " LIMIT ?", MAPPER, key, limit);
    }

    /** READNEXTs after {@code key} (exclusive). */
    public List<UserRecord> browseAfter(String key, int limit) {
        return jdbc.query(SELECT + "> ? COLLATE \"C\"" + KEY_ORDER + " LIMIT ?", MAPPER, key, limit);
    }

    /** READPREVs before {@code key} (exclusive), nearest first. */
    public List<UserRecord> browseBefore(String key, int limit) {
        return jdbc.query(SELECT + "< ? COLLATE \"C\"" + KEY_ORDER + " DESC LIMIT ?", MAPPER, key, limit);
    }

    public boolean existsAfter(String key) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_security WHERE usr_id > ? COLLATE \"C\")", Boolean.class, key));
    }

    /** READ ... UPDATE: locks the row until the surrounding transaction ends. */
    public Optional<UserRecord> readForUpdate(String userId) {
        return jdbc.query(SELECT + "= ? FOR UPDATE", MAPPER, userId)
                .stream().findFirst();
    }

    /** WRITE; throws {@link org.springframework.dao.DuplicateKeyException} for DUPKEY/DUPREC. */
    public void insert(UserRecord user) {
        jdbc.update("INSERT INTO user_security (" + COLUMNS + ") VALUES (?,?,?,?,?)", user.userId(),
                user.firstName(), user.lastName(), user.passwordHash(), user.userType());
    }

    /** REWRITE; returns the number of rows updated (0 = NOTFND). */
    public int rewrite(UserRecord user) {
        return jdbc.update("UPDATE user_security SET first_name = ?, last_name = ?, password_hash = ?, usr_type = ?"
                + " WHERE usr_id = ?", user.firstName(), user.lastName(), user.passwordHash(), user.userType(),
                user.userId());
    }

    /** DELETE; returns the number of rows deleted (0 = NOTFND). */
    public int delete(String userId) {
        return jdbc.update("DELETE FROM user_security WHERE usr_id = ?", userId);
    }
}
