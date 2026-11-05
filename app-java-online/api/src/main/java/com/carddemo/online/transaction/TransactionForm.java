package com.carddemo.online.transaction;

/**
 * Input fields of the COTRN02 map. Used as the request body of {@code POST /api/transactions} and returned
 * (normalised as COTRN02C leaves them on the re-sent map) as the data of error responses.
 */
public record TransactionForm(
        String acctId,
        String cardNum,
        String typeCd,
        String catCd,
        String source,
        String description,
        String amount,
        String origDate,
        String procDate,
        String merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String confirm) {
}
