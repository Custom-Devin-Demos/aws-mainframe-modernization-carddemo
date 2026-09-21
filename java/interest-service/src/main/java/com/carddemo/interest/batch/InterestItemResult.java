package com.carddemo.interest.batch;

import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.Transaction;
import java.util.List;

/**
 * Output of one processor step: zero or one interest Transaction for the current record, and
 * zero or one Account update emitted on control break.
 */
public record InterestItemResult(List<Transaction> transactions, List<Account> accountUpdates) {
    public static InterestItemResult empty() {
        return new InterestItemResult(List.of(), List.of());
    }
}
