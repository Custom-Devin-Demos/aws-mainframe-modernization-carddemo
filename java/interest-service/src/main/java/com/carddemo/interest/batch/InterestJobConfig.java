package com.carddemo.interest.batch;

import com.carddemo.interest.trace.Trace;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Batch job "interestCalcJob" equivalent to JCL INTCALC STEP15 (app/jcl/INTCALC.jcl line 22).
 *
 * <p>Job parameters: {@code runDate} (PARM='2022071800'), input/output file locations for
 * TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT.
 */
@Configuration
@Trace(program = "CBACT04C", paragraph = "PROCEDURE DIVISION", lines = "180-232")
public class InterestJobConfig {

    public static final String JOB_NAME = "interestCalcJob";
    public static final String PARAM_RUN_DATE = "runDate";
    public static final String PARAM_TCATBAL_FILE = "tcatbalFile";
    public static final String PARAM_XREF_FILE = "xrefFile";
    public static final String PARAM_DISCGRP_FILE = "discgrpFile";
    public static final String PARAM_ACCT_FILE = "acctFile";
    public static final String PARAM_TRAN_FILE = "tranFile";
}
