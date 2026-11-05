package com.carddemo.online.card;

/** Literal WS-ERROR-MSG / WS-RETURN-MSG / WS-INFO-MSG texts from COCRDLIC, COCRDSLC and COCRDUPC. */
final class CardMessages {

    private CardMessages() {
    }

    // COCRDLIC
    static final String LIST_ACCT_FILTER_INVALID = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    static final String LIST_CARD_FILTER_INVALID = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
    static final String MORE_THAN_ONE_ACTION = "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE";
    static final String INVALID_ACTION_CODE = "INVALID ACTION CODE";
    static final String NO_RECORDS_FOUND = "NO RECORDS FOUND FOR THIS SEARCH CONDITION.";
    static final String NO_MORE_RECORDS = "NO MORE RECORDS TO SHOW";
    static final String NO_PREVIOUS_PAGES = "NO PREVIOUS PAGES TO DISPLAY";
    static final String NO_MORE_PAGES = "NO MORE PAGES TO DISPLAY";
    static final String INFORM_REC_ACTIONS = "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD";

    // COCRDSLC / COCRDUPC search keys
    static final String PROMPT_FOR_ACCT = "Account number not provided";
    static final String PROMPT_FOR_CARD = "Card number not provided";
    static final String NO_SEARCH_CRITERIA = "No input received";
    static final String ACCT_FILTER_INVALID = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    static final String CARD_FILTER_INVALID = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
    static final String DID_NOT_FIND_ACCTCARD_COMBO = "Did not find cards for this search condition";
    static final String PROMPT_FOR_INPUT = "Please enter Account and Card Number";
    static final String DISPLAYING_DETAILS = "   Displaying requested details";

    // COCRDUPC
    static final String FOUND_CARD_FOR_UPDATE = "Details of selected card shown above";
    static final String PROMPT_FOR_CHANGES = "Update card details presented above.";
    static final String PROMPT_FOR_CONFIRMATION = "Changes validated.Press F5 to save";
    static final String CONFIRM_UPDATE_SUCCESS = "Changes committed to database";
    static final String INFORM_FAILURE = "Changes unsuccessful. Please try again";
    static final String PROMPT_FOR_NAME = "Card name not provided";
    static final String NAME_MUST_BE_ALPHA = "Card name can only contain alphabets and spaces";
    static final String NO_CHANGES_DETECTED = "No change detected with respect to values fetched.";
    static final String STATUS_MUST_BE_YES_NO = "Card Active Status must be Y or N";
    static final String EXPIRY_MONTH_NOT_VALID = "Card expiry month must be between 1 and 12";
    static final String EXPIRY_YEAR_NOT_VALID = "Invalid card expiry year";
    static final String COULD_NOT_LOCK = "Could not lock record for update";
    static final String DATA_CHANGED_BEFORE_UPDATE = "Record changed by some one else. Please review";
    static final String UPDATE_FAILED = "Update of record failed";
}
