-- CORPT00C: replaces the TRNRPT00 job written to the extra-partition TDQ 'JOBS' (internal reader).
-- One row per submitted report; jcl holds the job stream CORPT00C would have written.
CREATE TABLE report_request (
    request_id    BIGSERIAL    PRIMARY KEY,
    report_name   VARCHAR(10)  NOT NULL,             -- WS-REPORT-NAME: Monthly / Yearly / Custom
    start_date    DATE         NOT NULL,             -- PARM-START-DATE
    end_date      DATE         NOT NULL,             -- PARM-END-DATE
    requested_by  VARCHAR(8)   NOT NULL,             -- CDEMO-USER-ID
    requested_at  TIMESTAMP(6) NOT NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'SUBMITTED',
    jcl           TEXT         NOT NULL              -- JOB-DATA lines with the dates filled in
);
CREATE INDEX report_request_requested_by_idx ON report_request (requested_by);
