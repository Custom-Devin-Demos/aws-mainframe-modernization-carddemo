package com.carddemo.online.transaction;

/** One row of the COTRN00 list (TRNIDnn, TDATEnn, TDESCnn, TAMTnnn). */
public record TransactionListRow(String tranId, String date, String description, String amount) {
}
