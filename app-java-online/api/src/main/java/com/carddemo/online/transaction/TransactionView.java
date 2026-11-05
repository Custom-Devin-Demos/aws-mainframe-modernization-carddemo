package com.carddemo.online.transaction;

/** Output fields of the COTRN01 map, formatted as COTRN01C moves them. */
public record TransactionView(
        String tranId,
        String cardNum,
        String typeCd,
        String catCd,
        String source,
        String amount,
        String description,
        String origDate,
        String procDate,
        String merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip) {
}
