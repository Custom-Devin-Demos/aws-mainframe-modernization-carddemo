-- Relational schema derived from the CardDemo VSAM copybooks (app/cpy).
-- Column comments name the source COBOL field. Alphanumeric PIC X fields are
-- stored trimmed of trailing spaces; PIC 9 keys are stored as numbers.

-- USRSEC / CSUSR01Y SEC-USER-DATA (KSDS key SEC-USR-ID)
CREATE TABLE user_security (
    usr_id        VARCHAR(8)  PRIMARY KEY,           -- SEC-USR-ID     X(08)
    first_name    VARCHAR(20) NOT NULL DEFAULT '',   -- SEC-USR-FNAME  X(20)
    last_name     VARCHAR(20) NOT NULL DEFAULT '',   -- SEC-USR-LNAME  X(20)
    password_hash VARCHAR(60) NOT NULL,              -- SEC-USR-PWD    X(08), stored as a BCrypt hash
    usr_type      VARCHAR(1)    NOT NULL               -- SEC-USR-TYPE   X(01) 'A' admin / 'U' user
);

-- CUSTDAT / CVCUS01Y CUSTOMER-RECORD (KSDS key CUST-ID)
CREATE TABLE customer (
    cust_id               BIGINT      PRIMARY KEY,   -- CUST-ID                 9(09)
    first_name            VARCHAR(25) NOT NULL DEFAULT '',
    middle_name           VARCHAR(25) NOT NULL DEFAULT '',
    last_name             VARCHAR(25) NOT NULL DEFAULT '',
    addr_line_1           VARCHAR(50) NOT NULL DEFAULT '',
    addr_line_2           VARCHAR(50) NOT NULL DEFAULT '',
    addr_line_3           VARCHAR(50) NOT NULL DEFAULT '',  -- city in the online screens
    addr_state_cd         VARCHAR(2)  NOT NULL DEFAULT '',
    addr_country_cd       VARCHAR(3)  NOT NULL DEFAULT '',
    addr_zip              VARCHAR(10) NOT NULL DEFAULT '',
    phone_num_1           VARCHAR(15) NOT NULL DEFAULT '',
    phone_num_2           VARCHAR(15) NOT NULL DEFAULT '',
    ssn                   VARCHAR(9)  NOT NULL DEFAULT '',  -- CUST-SSN 9(09), kept as text to preserve leading zeros
    govt_issued_id        VARCHAR(20) NOT NULL DEFAULT '',
    dob                   DATE,                             -- CUST-DOB-YYYY-MM-DD X(10)
    eft_account_id        VARCHAR(10) NOT NULL DEFAULT '',
    pri_card_holder_ind   VARCHAR(1)    NOT NULL DEFAULT ' ',
    fico_credit_score     INTEGER     NOT NULL DEFAULT 0    -- 9(03)
);

-- ACCTDAT / CVACT01Y ACCOUNT-RECORD (KSDS key ACCT-ID)
CREATE TABLE account (
    acct_id               BIGINT        PRIMARY KEY,  -- ACCT-ID 9(11)
    active_status         VARCHAR(1)      NOT NULL DEFAULT ' ',
    curr_bal              NUMERIC(12,2) NOT NULL DEFAULT 0,   -- S9(10)V99
    credit_limit          NUMERIC(12,2) NOT NULL DEFAULT 0,
    cash_credit_limit     NUMERIC(12,2) NOT NULL DEFAULT 0,
    open_date             DATE,
    expiration_date       DATE,                               -- ACCT-EXPIRAION-DATE (sic)
    reissue_date          DATE,
    curr_cyc_credit       NUMERIC(12,2) NOT NULL DEFAULT 0,
    curr_cyc_debit        NUMERIC(12,2) NOT NULL DEFAULT 0,
    addr_zip              VARCHAR(10)   NOT NULL DEFAULT '',
    group_id              VARCHAR(10)   NOT NULL DEFAULT ''
);

-- CARDDAT / CVACT02Y CARD-RECORD (KSDS key CARD-NUM, AIX CARDAIX on CARD-ACCT-ID)
CREATE TABLE card (
    card_num              VARCHAR(16)  PRIMARY KEY,
    acct_id               BIGINT       NOT NULL,      -- CARD-ACCT-ID 9(11)
    cvv_cd                INTEGER      NOT NULL DEFAULT 0,
    embossed_name         VARCHAR(50)  NOT NULL DEFAULT '',
    expiration_date       DATE,                       -- CARD-EXPIRAION-DATE (sic)
    active_status         VARCHAR(1)     NOT NULL DEFAULT ' '
);
CREATE INDEX card_acct_id_idx ON card (acct_id);       -- CARDAIX

-- CCXREF / CVACT03Y CARD-XREF-RECORD (KSDS key XREF-CARD-NUM, AIX CXACAIX on XREF-ACCT-ID)
CREATE TABLE card_xref (
    card_num              VARCHAR(16)  PRIMARY KEY,
    cust_id               BIGINT       NOT NULL,
    acct_id               BIGINT       NOT NULL
);
CREATE INDEX card_xref_acct_id_idx ON card_xref (acct_id);  -- CXACAIX
CREATE INDEX card_xref_cust_id_idx ON card_xref (cust_id);

-- TRANTYPE / CVTRA03Y TRAN-TYPE-RECORD
CREATE TABLE tran_type (
    tran_type             VARCHAR(2)   PRIMARY KEY,
    description           VARCHAR(50)  NOT NULL DEFAULT ''
);

-- TRANCATG / CVTRA04Y TRAN-CAT-RECORD (key TRAN-TYPE-CD + TRAN-CAT-CD)
CREATE TABLE tran_category (
    tran_type_cd          VARCHAR(2)   NOT NULL,
    tran_cat_cd           INTEGER      NOT NULL,
    description           VARCHAR(50)  NOT NULL DEFAULT '',
    PRIMARY KEY (tran_type_cd, tran_cat_cd)
);

-- TCATBALF / CVTRA01Y TRAN-CAT-BAL-RECORD (key ACCT-ID + TYPE-CD + CAT-CD)
CREATE TABLE tran_cat_balance (
    acct_id               BIGINT        NOT NULL,
    tran_type_cd          VARCHAR(2)    NOT NULL,
    tran_cat_cd           INTEGER       NOT NULL,
    balance               NUMERIC(11,2) NOT NULL DEFAULT 0,  -- S9(09)V99
    PRIMARY KEY (acct_id, tran_type_cd, tran_cat_cd)
);

-- DISCGRP / CVTRA02Y DIS-GROUP-RECORD (key GROUP-ID + TYPE-CD + CAT-CD)
CREATE TABLE disclosure_group (
    acct_group_id         VARCHAR(10)   NOT NULL,
    tran_type_cd          VARCHAR(2)    NOT NULL,
    tran_cat_cd           INTEGER       NOT NULL,
    int_rate              NUMERIC(6,2)  NOT NULL DEFAULT 0,  -- S9(04)V99
    PRIMARY KEY (acct_group_id, tran_type_cd, tran_cat_cd)
);

-- TRANSACT / CVTRA05Y TRAN-RECORD (KSDS key TRAN-ID)
CREATE TABLE transaction (
    tran_id               VARCHAR(16)   PRIMARY KEY,   -- numeric text, zero padded
    tran_type_cd          VARCHAR(2)    NOT NULL DEFAULT '',
    tran_cat_cd           INTEGER       NOT NULL DEFAULT 0,
    source                VARCHAR(10)   NOT NULL DEFAULT '',
    description           VARCHAR(100)  NOT NULL DEFAULT '',
    amount                NUMERIC(11,2) NOT NULL DEFAULT 0,   -- S9(09)V99
    merchant_id           BIGINT        NOT NULL DEFAULT 0,   -- 9(09)
    merchant_name         VARCHAR(50)   NOT NULL DEFAULT '',
    merchant_city         VARCHAR(50)   NOT NULL DEFAULT '',
    merchant_zip          VARCHAR(10)   NOT NULL DEFAULT '',
    card_num              VARCHAR(16)   NOT NULL DEFAULT '',
    orig_ts               TIMESTAMP(6),                       -- X(26) 'YYYY-MM-DD HH:MM:SS.ffffff'
    proc_ts               TIMESTAMP(6)
);
CREATE INDEX transaction_card_num_idx ON transaction (card_num);

-- Referential integrity implied by the VSAM cross-references (sample data satisfies all of these).
ALTER TABLE card             ADD CONSTRAINT card_account_fk       FOREIGN KEY (acct_id) REFERENCES account (acct_id);
ALTER TABLE card_xref        ADD CONSTRAINT card_xref_card_fk     FOREIGN KEY (card_num) REFERENCES card (card_num);
ALTER TABLE card_xref        ADD CONSTRAINT card_xref_customer_fk FOREIGN KEY (cust_id) REFERENCES customer (cust_id);
ALTER TABLE card_xref        ADD CONSTRAINT card_xref_account_fk  FOREIGN KEY (acct_id) REFERENCES account (acct_id);
ALTER TABLE tran_category    ADD CONSTRAINT tran_category_type_fk FOREIGN KEY (tran_type_cd) REFERENCES tran_type (tran_type);
ALTER TABLE tran_cat_balance ADD CONSTRAINT tran_cat_balance_account_fk  FOREIGN KEY (acct_id) REFERENCES account (acct_id);
ALTER TABLE tran_cat_balance ADD CONSTRAINT tran_cat_balance_category_fk
    FOREIGN KEY (tran_type_cd, tran_cat_cd) REFERENCES tran_category (tran_type_cd, tran_cat_cd);
ALTER TABLE transaction      ADD CONSTRAINT transaction_card_fk     FOREIGN KEY (card_num) REFERENCES card (card_num);
ALTER TABLE transaction      ADD CONSTRAINT transaction_category_fk
    FOREIGN KEY (tran_type_cd, tran_cat_cd) REFERENCES tran_category (tran_type_cd, tran_cat_cd);
