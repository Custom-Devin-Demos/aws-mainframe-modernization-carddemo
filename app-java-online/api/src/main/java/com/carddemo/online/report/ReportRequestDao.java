package com.carddemo.online.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class ReportRequestDao {

    private static final RowMapper<ReportRequest> MAPPER = (rs, i) -> new ReportRequest(rs.getLong("request_id"),
            rs.getString("report_name"), rs.getObject("start_date", LocalDate.class),
            rs.getObject("end_date", LocalDate.class), rs.getString("requested_by"),
            rs.getObject("requested_at", LocalDateTime.class), rs.getString("status"), rs.getString("jcl"));

    private final JdbcTemplate jdbc;

    ReportRequestDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    ReportRequest insert(String reportName, LocalDate start, LocalDate end, String requestedBy,
            LocalDateTime requestedAt, String jcl) {
        return jdbc.queryForObject("INSERT INTO report_request (report_name, start_date, end_date, requested_by,"
                + " requested_at, jcl) VALUES (?,?,?,?,?,?) RETURNING *", MAPPER,
                reportName, start, end, requestedBy, requestedAt, jcl);
    }

    Optional<ReportRequest> findById(long requestId) {
        List<ReportRequest> rows = jdbc.query("SELECT * FROM report_request WHERE request_id = ?", MAPPER,
                requestId);
        return rows.stream().findFirst();
    }
}
