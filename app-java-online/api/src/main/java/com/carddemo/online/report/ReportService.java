package com.carddemo.online.report;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.session.SessionUser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CORPT00C PROCESS-ENTER-KEY / SUBMIT-JOB-TO-INTRDR: validates the report selection and date range and, instead
 * of writing the TRNRPT00 job to the TDQ 'JOBS', persists a {@code report_request} row.
 */
@Service
public class ReportService {

    static final String MSG_SELECT_TYPE = "Select a report type to print report...";
    static final String MSG_TDQ_WRITE = "Unable to Write TDQ (JOBS)...";
    static final String MSG_SUBMITTED = " report submitted for printing ...";

    static final String FIELD_REPORT_TYPE = "reportType";
    static final String FIELD_CONFIRM = "confirm";

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRequestDao dao;
    private final Clock clock;

    public ReportService(ReportRequestDao dao, ObjectProvider<Clock> clock) {
        this.dao = dao;
        this.clock = clock.getIfAvailable(Clock::systemDefaultZone);
    }

    @Transactional
    public ScreenResponse<ReportRequest> submit(ReportRequestBody body, SessionUser user) {
        String type = body.reportType() == null ? "" : body.reportType().trim().toUpperCase(Locale.ROOT);
        String reportName;
        String start;
        String end;
        LocalDate today = LocalDate.now(clock);
        switch (type) {
            case "MONTHLY" -> {
                // PROCESS-ENTER-KEY: first day of the current month to the day before the first of next month
                reportName = "Monthly";
                start = today.withDayOfMonth(1).toString();
                end = today.with(TemporalAdjusters.lastDayOfMonth()).toString();
            }
            case "YEARLY" -> {
                reportName = "Yearly";
                start = String.format("%04d-01-01", today.getYear());
                end = String.format("%04d-12-31", today.getYear());
            }
            case "CUSTOM" -> {
                reportName = "Custom";
                String[] range = validateCustomRange(body.startDate(), body.endDate());
                start = range[0];
                end = range[1];
            }
            // PROCESS-ENTER-KEY: WHEN OTHER
            default -> throw CardDemoException.validation(MSG_SELECT_TYPE, FIELD_REPORT_TYPE);
        }

        // SUBMIT-JOB-TO-INTRDR: CONFIRMI = SPACES OR LOW-VALUES
        String confirm = CobolText.rtrim(body.confirm());
        if (CobolText.isBlank(confirm)) {
            throw CardDemoException.validation("Please confirm to print the " + reportName + " report...",
                    FIELD_CONFIRM);
        }
        switch (confirm) {
            case "Y", "y" -> {
                // continue to the job submission
            }
            case "N", "n" -> {
                // INITIALIZE-ALL-FIELDS: screen cleared, no message, cursor on Monthly
                return new ScreenResponse<>(null, null, null, FIELD_REPORT_TYPE, null);
            }
            default -> {
                String shown = confirm.split(" ", 2)[0];
                throw CardDemoException.validation("\"" + shown + "\" is not a valid value to confirm...",
                        FIELD_CONFIRM);
            }
        }

        // SUBMIT-JOB-TO-INTRDR: WRITEQ TD QUEUE('JOBS') replaced by an INSERT
        ReportRequest saved;
        try {
            saved = dao.insert(reportName, LocalDate.parse(start), LocalDate.parse(end), user.userId(),
                    LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS), ReportJcl.jobStream(start, end));
        } catch (DataAccessException e) {
            // WIRTE-JOBSUB-TDQ: RESP other than NORMAL
            log.error("report_request insert failed", e);
            throw CardDemoException.failure(MSG_TDQ_WRITE, FIELD_REPORT_TYPE);
        }
        return ScreenResponse.success(saved, reportName + MSG_SUBMITTED).withField(FIELD_REPORT_TYPE);
    }

    @Transactional(readOnly = true)
    public ScreenResponse<ReportRequest> find(String requestId) {
        String id = requestId == null ? "" : requestId.trim();
        if (!CobolText.isNumeric(id) || id.length() > 18) {
            throw CardDemoException.notFound(ReportController.MSG_NOT_FOUND, null);
        }
        return dao.findById(Long.parseLong(id))
                .map(ScreenResponse::of)
                .orElseThrow(() -> CardDemoException.notFound(ReportController.MSG_NOT_FOUND, null));
    }

    /** PROCESS-ENTER-KEY, WHEN CUSTOMI: returns the validated {start, end} as 'YYYY-MM-DD'. */
    private static String[] validateCustomRange(ReportDate startDate, ReportDate endDate) {
        ReportDate s = startDate == null ? new ReportDate(null, null, null) : startDate;
        ReportDate e = endDate == null ? new ReportDate(null, null, null) : endDate;

        // empty checks, in screen order
        requireNotEmpty(s.month(), "Start Date - Month can NOT be empty...", "startDate.month");
        requireNotEmpty(s.day(), "Start Date - Day can NOT be empty...", "startDate.day");
        requireNotEmpty(s.year(), "Start Date - Year can NOT be empty...", "startDate.year");
        requireNotEmpty(e.month(), "End Date - Month can NOT be empty...", "endDate.month");
        requireNotEmpty(e.day(), "End Date - Day can NOT be empty...", "endDate.day");
        requireNotEmpty(e.year(), "End Date - Year can NOT be empty...", "endDate.year");

        // NUMVAL-C into WS-NUM-99 / WS-NUM-9999, then IS NOT NUMERIC / > '12' / > '31' checks
        String sMm = twoDigits(s.month(), 12, "Start Date - Not a valid Month...", "startDate.month");
        String sDd = twoDigits(s.day(), 31, "Start Date - Not a valid Day...", "startDate.day");
        String sYyyy = fourDigits(s.year(), "Start Date - Not a valid Year...", "startDate.year");
        String eMm = twoDigits(e.month(), 12, "End Date - Not a valid Month...", "endDate.month");
        String eDd = twoDigits(e.day(), 31, "End Date - Not a valid Day...", "endDate.day");
        String eYyyy = fourDigits(e.year(), "End Date - Not a valid Year...", "endDate.year");

        String start = sYyyy + "-" + sMm + "-" + sDd;
        String end = eYyyy + "-" + eMm + "-" + eDd;

        // CALL 'CSUTLDTC' for each date (cursor goes to the month field)
        if (!CsutldtcDateValidator.acceptedByCorpt00c(start)) {
            throw CardDemoException.validation("Start Date - Not a valid date...", "startDate.month");
        }
        if (!CsutldtcDateValidator.acceptedByCorpt00c(end)) {
            throw CardDemoException.validation("End Date - Not a valid date...", "endDate.month");
        }
        return new String[] {start, end};
    }

    private static void requireNotEmpty(String value, String message, String field) {
        if (CobolText.isBlank(value)) {
            throw CardDemoException.validation(message, field);
        }
    }

    /** SDTMM / SDTDD style 2-character numeric field with an upper bound. */
    private static String twoDigits(String value, int max, String message, String field) {
        String v = value.trim();
        if (!CobolText.isNumeric(v) || v.length() > 2 || Integer.parseInt(v) > max) {
            throw CardDemoException.validation(message, field);
        }
        return String.format("%02d", Integer.parseInt(v));
    }

    /** SDTYYYY style 4-character numeric field. */
    private static String fourDigits(String value, String message, String field) {
        String v = value.trim();
        if (!CobolText.isNumeric(v) || v.length() > 4) {
            throw CardDemoException.validation(message, field);
        }
        return String.format("%04d", Integer.parseInt(v));
    }
}
