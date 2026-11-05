package com.carddemo.online.report;

/**
 * CORPT0AI input: {@code reportType} replaces the MONTHLY / YEARLY / CUSTOM selection fields
 * (MONTHLY, YEARLY or CUSTOM); the date range is only used for CUSTOM.
 */
public record ReportRequestBody(String reportType, ReportDate startDate, ReportDate endDate, String confirm) {
}
