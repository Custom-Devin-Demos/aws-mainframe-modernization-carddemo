package com.carddemo.online.billpay;

import java.math.BigDecimal;

/**
 * COBIL0AO output: the account entered and its current balance (CURBAL). After a successful payment the
 * new transaction id, card number and amount are returned as well.
 */
public record BillPayScreen(String acctId, BigDecimal currentBalance, String tranId, String cardNum,
        BigDecimal amount) {

    static BillPayScreen balance(String acctId, BigDecimal currentBalance) {
        return new BillPayScreen(acctId, currentBalance, null, null, null);
    }
}
