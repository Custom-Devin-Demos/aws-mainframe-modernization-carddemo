package com.carddemo.online.report;

/** The three CORPT0A input fields of a date: SDTMM / SDTDD / SDTYYYY (or EDTMM / EDTDD / EDTYYYY). */
public record ReportDate(String month, String day, String year) {
}
