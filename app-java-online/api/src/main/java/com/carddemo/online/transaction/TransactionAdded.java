package com.carddemo.online.transaction;

/** Result of a successful COTRN02C WRITE: the allocated TRAN-ID. */
public record TransactionAdded(String tranId) {
}
