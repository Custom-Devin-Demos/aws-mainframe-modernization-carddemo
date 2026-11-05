package com.carddemo.online.report;

import java.util.List;

/** CORPT00C JOB-DATA: the TRNRPT00 job stream, with PARM-START-DATE / PARM-END-DATE filled in. */
final class ReportJcl {

    private ReportJcl() {
    }

    static String jobStream(String startDate, String endDate) {
        return String.join("\n", List.of(
                "//TRNRPT00 JOB 'TRAN REPORT',CLASS=A,MSGCLASS=0,",
                "// NOTIFY=&SYSUID",
                "//*",
                "//JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')",
                "//*",
                "//STEP10 EXEC PROC=TRANREPT",
                "//*",
                "//STEP05R.SYMNAMES DD *",
                "TRAN-CARD-NUM,263,16,ZD",
                "TRAN-PROC-DT,305,10,CH",
                "PARM-START-DATE,C'" + startDate + "'",
                "PARM-END-DATE,C'" + endDate + "'",
                "/*",
                "//STEP10R.DATEPARM DD *",
                startDate + " " + endDate,
                "/*",
                "/*EOF"));
    }
}
