package com.carddemo.online.account;

/** Literal WS-RETURN-MSG / WS-INFO-MSG texts from COACTVWC and COACTUPC. */
final class AccountMessages {

    private AccountMessages() {
    }

    /** NO-SEARCH-CRITERIA-RECEIVED (both programs). */
    static final String NO_INPUT = "No input received";
    /** COACTVWC 2210-EDIT-ACCOUNT. */
    static final String VIEW_ACCT_INVALID = "Account Filter must  be a non-zero 11 digit number";
    /** COACTUPC 1210-EDIT-ACCOUNT. */
    static final String UPDATE_ACCT_INVALID = "Account Number if supplied must be a 11 digit Non-Zero Number";

    /** COACTUPC 3250-SETUP-INFOMSG PROMPT-FOR-CHANGES. */
    static final String PROMPT_FOR_CHANGES = "Update account details presented above.";
    /** PROMPT-FOR-CONFIRMATION. */
    static final String PROMPT_FOR_CONFIRMATION = "Changes validated.Press F5 to save";
    /** CONFIRM-UPDATE-SUCCESS. */
    static final String UPDATE_SUCCESS = "Changes committed to database";
    /** NO-CHANGES-DETECTED. */
    static final String NO_CHANGES = "No change detected with respect to values fetched.";
    /** COULD-NOT-LOCK-ACCT-FOR-UPDATE. */
    static final String LOCK_ACCT_FAILED = "Could not lock account record for update";
    /** COULD-NOT-LOCK-CUST-FOR-UPDATE. */
    static final String LOCK_CUST_FAILED = "Could not lock customer record for update";
    /** DATA-WAS-CHANGED-BEFORE-UPDATE. */
    static final String DATA_CHANGED = "Record changed by some one else. Please review";
    /** LOCKED-BUT-UPDATE-FAILED. */
    static final String UPDATE_FAILED = "Update of record failed";

    /** EXEC CICS READ RESP / RESP2 values for NOTFND, as moved into ERROR-RESP / ERROR-RESP2 (X(10)). */
    private static final String NOTFND_RESP = "000000013 ";
    private static final String NOTFND_RESP2 = "000000080 ";

    /** 9200-GETCARDXREF-BYACCT NOTFND. */
    static String xrefNotFound(String acctId11) {
        return ("Account:" + acctId11 + " not found in Cross ref file.  Resp:" + NOTFND_RESP + " Reas:" + NOTFND_RESP2)
                .stripTrailing();
    }

    /** 9300-GETACCTDATA-BYACCT NOTFND. */
    static String accountNotFound(String acctId11) {
        return ("Account:" + acctId11 + " not found in Acct Master file.Resp:" + NOTFND_RESP + " Reas:" + NOTFND_RESP2)
                .stripTrailing();
    }

    /** 9400-GETCUSTDATA-BYCUST NOTFND. */
    static String customerNotFound(String custId9) {
        return ("CustId:" + custId9 + " not found in customer master.Resp: " + NOTFND_RESP + " REAS:" + NOTFND_RESP2)
                .stripTrailing();
    }
}
