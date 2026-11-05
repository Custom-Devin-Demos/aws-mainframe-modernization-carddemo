package com.carddemo.online.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class ReportIT extends AbstractIntegrationTest {

    private static final ReportDate VALID_START = new ReportDate("01", "05", "2022");
    private static final ReportDate VALID_END = new ReportDate("12", "31", "2022");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void cleanUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS report_it_fail ON report_request");
        jdbc.execute("DROP FUNCTION IF EXISTS report_it_fail()");
    }

    private ResultActions submit(String type, ReportDate start, ReportDate end, String confirm) throws Exception {
        String body = json.writeValueAsString(new ReportRequestBody(type, start, end, confirm));
        return mvc.perform(as(USER, post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private ResultActions custom(ReportDate start, ReportDate end) throws Exception {
        return submit("CUSTOM", start, end, "Y");
    }

    private int requestCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM report_request", Integer.class);
    }

    private void expectError(ResultActions result, String message, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value(field))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void requiresSignOn() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }

    @Test
    void monthlyReportIsPersistedAndReadable() throws Exception {
        LocalDate today = LocalDate.now();
        int before = requestCount();
        // PROCESS-ENTER-KEY: MONTHLYI selected -> SUBMIT-JOB-TO-INTRDR -> 'Monthly report submitted ...'
        String body = submit("MONTHLY", null, null, "Y")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Monthly report submitted for printing ..."))
                .andExpect(jsonPath("$.messageType").value("SUCCESS"))
                .andExpect(jsonPath("$.field").value("reportType"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist())
                .andExpect(jsonPath("$.data.reportName").value("Monthly"))
                .andExpect(jsonPath("$.data.startDate").value(today.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.data.endDate")
                        .value(today.with(TemporalAdjusters.lastDayOfMonth()).toString()))
                .andExpect(jsonPath("$.data.requestedBy").value("USER0001"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.jcl").value(containsString("//TRNRPT00 JOB 'TRAN REPORT'")))
                .andReturn().getResponse().getContentAsString();
        assertThat(requestCount()).isEqualTo(before + 1);

        JsonNode data = json.readTree(body).get("data");
        mvc.perform(as(ADMIN, get("/api/reports/" + data.get("requestId").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportName").value("Monthly"))
                .andExpect(jsonPath("$.data.jcl").value(data.get("jcl").asText()));
    }

    @Test
    void yearlyReport() throws Exception {
        int year = LocalDate.now().getYear();
        // PROCESS-ENTER-KEY: YEARLYI selected
        submit("yearly", null, null, "y")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Yearly report submitted for printing ..."))
                .andExpect(jsonPath("$.field").value("reportType"))
                .andExpect(jsonPath("$.data.startDate").value(year + "-01-01"))
                .andExpect(jsonPath("$.data.endDate").value(year + "-12-31"))
                .andExpect(jsonPath("$.data.jcl")
                        .value(containsString("PARM-START-DATE,C'" + year + "-01-01'")));
    }

    @Test
    void customReportNormalisesTheEnteredDates() throws Exception {
        // PROCESS-ENTER-KEY: CUSTOMI -> NUMVAL-C into WS-NUM-99 / WS-NUM-9999, CSUTLDTC ok
        custom(new ReportDate("1", "5", "2022"), new ReportDate(" 2", "28", "2022"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Custom report submitted for printing ..."))
                .andExpect(jsonPath("$.field").value("reportType"))
                .andExpect(jsonPath("$.data.startDate").value("2022-01-05"))
                .andExpect(jsonPath("$.data.endDate").value("2022-02-28"))
                .andExpect(jsonPath("$.data.jcl").value(containsString("2022-01-05 2022-02-28")));
    }

    @Test
    void customDateBeforeLilianEpochIsAccepted() throws Exception {
        // PROCESS-ENTER-KEY: CSUTLDTC-RESULT-MSG-NUM = '2513' is tolerated
        custom(new ReportDate("01", "01", "1500"), VALID_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("1500-01-01"));
    }

    @Test
    void noReportTypeSelected() throws Exception {
        int before = requestCount();
        // PROCESS-ENTER-KEY: WHEN OTHER
        expectError(submit(null, null, null, "Y"), "Select a report type to print report...", "reportType");
        expectError(submit("WEEKLY", null, null, "Y"), "Select a report type to print report...", "reportType");
        assertThat(requestCount()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"MONTHLY, Monthly", "YEARLY, Yearly", "CUSTOM, Custom"})
    void blankConfirm(String type, String name) throws Exception {
        // SUBMIT-JOB-TO-INTRDR: CONFIRMI = SPACES OR LOW-VALUES
        expectError(submit(type, VALID_START, VALID_END, " "), "Please confirm to print the " + name + " report...",
                "confirm");
        expectError(submit(type, VALID_START, VALID_END, null), "Please confirm to print the " + name + " report...",
                "confirm");
    }

    @Test
    void confirmNoClearsTheScreen() throws Exception {
        int before = requestCount();
        // SUBMIT-JOB-TO-INTRDR: CONFIRMI = 'N' OR 'n' -> INITIALIZE-ALL-FIELDS, no message
        for (String n : new String[] {"N", "n"}) {
            submit("MONTHLY", null, null, n)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.message").doesNotExist())
                    .andExpect(jsonPath("$.field").value("reportType"))
                    .andExpect(jsonPath("$.nextProgram").doesNotExist());
        }
        assertThat(requestCount()).isEqualTo(before);
    }

    @Test
    void invalidConfirmValue() throws Exception {
        // SUBMIT-JOB-TO-INTRDR: WHEN OTHER -> '"' CONFIRMI DELIMITED BY SPACE '" is not a valid value ...'
        expectError(submit("YEARLY", null, null, "X"), "\"X\" is not a valid value to confirm...", "confirm");
        expectError(submit("CUSTOM", VALID_START, VALID_END, "1"), "\"1\" is not a valid value to confirm...",
                "confirm");
    }

    @ParameterizedTest(name = "[{index}] {6}")
    @CsvSource(nullValues = "NULL", value = {
        // PROCESS-ENTER-KEY (CUSTOMI): SDTMMI = SPACES OR LOW-VALUES
        "'',   '',   '',    '',   '',   '',     Start Date - Month can NOT be empty..., startDate.month",
        "NULL, 05,   2022,  12,   31,   2022,   Start Date - Month can NOT be empty..., startDate.month",
        // SDTDDI = SPACES OR LOW-VALUES
        "01,   ' ',  2022,  12,   31,   2022,   Start Date - Day can NOT be empty..., startDate.day",
        // SDTYYYYI = SPACES OR LOW-VALUES
        "01,   05,   '',    12,   31,   2022,   Start Date - Year can NOT be empty..., startDate.year",
        // EDTMMI = SPACES OR LOW-VALUES
        "01,   05,   2022,  '',   31,   2022,   End Date - Month can NOT be empty..., endDate.month",
        // EDTDDI = SPACES OR LOW-VALUES
        "01,   05,   2022,  12,   '',   2022,   End Date - Day can NOT be empty..., endDate.day",
        // EDTYYYYI = SPACES OR LOW-VALUES
        "01,   05,   2022,  12,   31,   '',     End Date - Year can NOT be empty..., endDate.year",
        // SDTMMI IS NOT NUMERIC OR > '12' (empty checks all come first)
        "13,   05,   2022,  12,   31,   2022,   Start Date - Not a valid Month..., startDate.month",
        "ab,   32,   20x2,  13,   32,   20x2,   Start Date - Not a valid Month..., startDate.month",
        // SDTDDI IS NOT NUMERIC OR > '31'
        "01,   32,   2022,  12,   31,   2022,   Start Date - Not a valid Day..., startDate.day",
        "01,   x1,   2022,  12,   31,   2022,   Start Date - Not a valid Day..., startDate.day",
        // SDTYYYYI IS NOT NUMERIC
        "01,   05,   20x2,  12,   31,   2022,   Start Date - Not a valid Year..., startDate.year",
        // EDTMMI IS NOT NUMERIC OR > '12'
        "01,   05,   2022,  13,   31,   2022,   End Date - Not a valid Month..., endDate.month",
        // EDTDDI IS NOT NUMERIC OR > '31'
        "01,   05,   2022,  12,   32,   2022,   End Date - Not a valid Day..., endDate.day",
        // EDTYYYYI IS NOT NUMERIC
        "01,   05,   2022,  12,   31,   2O22,   End Date - Not a valid Year..., endDate.year",
        // CALL 'CSUTLDTC' (start date) severity <> 0 and msg <> 2513
        "02,   30,   2023,  12,   31,   2023,   Start Date - Not a valid date..., startDate.month",
        "00,   10,   2023,  12,   31,   2023,   Start Date - Not a valid date..., startDate.month",
        "01,   00,   2023,  12,   31,   2023,   Start Date - Not a valid date..., startDate.month",
        "01,   01,   0000,  12,   31,   2023,   Start Date - Not a valid date..., startDate.month",
        // CALL 'CSUTLDTC' (end date)
        "01,   01,   2023,  04,   31,   2023,   End Date - Not a valid date..., endDate.month",
        "01,   01,   2023,  02,   29,   2023,   End Date - Not a valid date..., endDate.month",
    })
    void customDateValidation(String sm, String sd, String sy, String em, String ed, String ey, String message,
            String field) throws Exception {
        int before = requestCount();
        expectError(custom(new ReportDate(sm, sd, sy), new ReportDate(em, ed, ey)), message, field);
        assertThat(requestCount()).isEqualTo(before);
    }

    @Test
    void customWithoutDatesIsAStartMonthError() throws Exception {
        // PROCESS-ENTER-KEY (CUSTOMI): the date checks run before SUBMIT-JOB-TO-INTRDR checks CONFIRMI
        expectError(submit("CUSTOM", null, null, null), "Start Date - Month can NOT be empty...", "startDate.month");
    }

    @Test
    void jobSubmissionFailure() throws Exception {
        jdbc.execute("CREATE FUNCTION report_it_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
                + " RAISE EXCEPTION 'forced by ReportIT'; END $$");
        jdbc.execute("CREATE TRIGGER report_it_fail BEFORE INSERT ON report_request"
                + " FOR EACH ROW EXECUTE FUNCTION report_it_fail()");
        // WIRTE-JOBSUB-TDQ: RESP other than NORMAL
        submit("MONTHLY", null, null, "Y")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to Write TDQ (JOBS)..."))
                .andExpect(jsonPath("$.field").value("reportType"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    @Test
    void unknownReportRequest() throws Exception {
        mvc.perform(as(USER, get("/api/reports/999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Report request NOT found..."))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
        mvc.perform(as(USER, get("/api/reports/abc")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Report request NOT found..."));
    }
}
