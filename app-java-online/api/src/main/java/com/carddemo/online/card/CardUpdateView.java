package com.carddemo.online.card;

/**
 * COCRDUP screen payload after a PUT.
 *
 * @param card     fields to show (new values, or the refreshed record after a concurrent change)
 * @param original CCUP-OLD-CARDDATA to send back with the next PUT
 * @param state    CCUP-CHANGE-ACTION: SHOW_DETAILS, CHANGES_NOT_OK, CHANGES_OK_NOT_CONFIRMED,
 *                 CHANGES_OKAYED_AND_DONE, CHANGES_FAILED
 */
public record CardUpdateView(CardDetailView card, CardUpdateRequest.Original original, String state) {
}
