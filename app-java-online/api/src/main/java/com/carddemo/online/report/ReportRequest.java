package com.carddemo.online.report;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A persisted report_request row (the job CORPT00C submitted to the internal reader). */
public record ReportRequest(long requestId, String reportName, LocalDate startDate, LocalDate endDate,
        String requestedBy, LocalDateTime requestedAt, String status, String jcl) {
}
