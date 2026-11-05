package com.carddemo.online.card;

/**
 * COCRDUP input. {@code original} carries the CCUP-OLD-DETAILS the client fetched (the COMMAREA copy
 * used for the no-change and changed-by-someone-else checks). {@code confirm} is PF5.
 */
public record CardUpdateRequest(
        String acctId,
        String embossedName,
        String activeStatus,
        String expiryMonth,
        String expiryYear,
        Original original,
        boolean confirm) {

    /** CCUP-OLD-CARDDATA. */
    public record Original(String embossedName, String activeStatus, String expiryYear, String expiryMonth,
            String expiryDay) {
    }
}
