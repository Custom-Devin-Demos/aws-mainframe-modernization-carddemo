package com.carddemo.online.card;

import com.carddemo.online.common.KeysetPage;
import java.util.List;

/**
 * COCRDLI screen payload.
 *
 * @param page            the (up to) 7 rows shown, with PF7/PF8 keys
 * @param infoMessage     INFOMSG line, or null when the COBOL leaves it dark
 * @param errorRows       1-based rows whose select code was flagged (WS-ROW-CRDSELECT-ERROR)
 * @param selectedAcctId  CDEMO-ACCT-ID passed to the next program on a valid S/U selection
 * @param selectedCardNum CDEMO-CARD-NUM passed to the next program on a valid S/U selection
 */
public record CardListView(
        KeysetPage<CardRow> page,
        String infoMessage,
        List<Integer> errorRows,
        String selectedAcctId,
        String selectedCardNum) {
}
