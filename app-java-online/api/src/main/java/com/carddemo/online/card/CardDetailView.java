package com.carddemo.online.card;

/** COCRDSL / COCRDUP screen fields: search keys, card details and the INFOMSG line. */
public record CardDetailView(
        String acctId,
        String cardNum,
        String embossedName,
        String activeStatus,
        String expiryMonth,
        String expiryYear,
        String expiryDay,
        String infoMessage) {

    static CardDetailView keysOnly(String acctId, String cardNum, String infoMessage) {
        return new CardDetailView(acctId, cardNum, null, null, null, null, null, infoMessage);
    }
}
