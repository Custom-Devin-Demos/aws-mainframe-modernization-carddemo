package com.carddemo.online.card;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;

/**
 * Edits of the account / card number search keys (2210-EDIT-ACCOUNT, 2220-EDIT-CARD in COCRDSLC and
 * 1210-EDIT-ACCOUNT, 1220-EDIT-CARD in COCRDUPC). A '*' (the blank-field marker the COBOL writes
 * back to the screen) counts as blank.
 */
final class CardKeys {

    static final String ACCT_ID = "acctId";
    static final String CARD_NUM = "cardNum";

    private CardKeys() {
    }

    /** Validated search keys; numbers are the 11 / 16 digit COBOL values. */
    record Keys(String acctId, String cardNum) {
    }

    static boolean isBlankOrZero(String value, int digits) {
        if (CobolText.isBlank(value) || "*".equals(value.trim())) {
            return true;
        }
        String v = value.trim();
        return v.length() == digits && v.chars().allMatch(c -> c == '0');
    }

    /** {@code IS NUMERIC} on a fully filled PIC X(n) field. */
    static boolean isDigits(String value, int digits) {
        String v = value.trim();
        return v.length() == digits && CobolText.isNumeric(v);
    }

    /**
     * Runs both edits in COBOL order; the first message wins (IF WS-RETURN-MSG-OFF), and
     * 'No input received' replaces it when both keys are blank. The cursor goes to the account field
     * when it is blank or invalid, otherwise to the card field (1300-SETUP-SCREEN-ATTRS).
     */
    static Keys validate(String acctId, String cardNum, Object screenData) {
        String message = null;
        String field = null;
        boolean acctBlank = isBlankOrZero(acctId, 11);
        boolean cardBlank = isBlankOrZero(cardNum, 16);
        if (acctBlank) {
            message = CardMessages.PROMPT_FOR_ACCT;
            field = ACCT_ID;
        } else if (!isDigits(acctId, 11)) {
            message = CardMessages.ACCT_FILTER_INVALID;
            field = ACCT_ID;
        }
        if (cardBlank) {
            if (message == null) {
                message = CardMessages.PROMPT_FOR_CARD;
                field = CARD_NUM;
            }
        } else if (!isDigits(cardNum, 16) && message == null) {
            message = CardMessages.CARD_FILTER_INVALID;
            field = CARD_NUM;
        }
        if (acctBlank && cardBlank) {
            message = CardMessages.NO_SEARCH_CRITERIA;
        }
        if (message != null) {
            throw CardDemoException.validation(message, field).withData(screenData);
        }
        return new Keys(acctId.trim(), cardNum.trim());
    }
}
