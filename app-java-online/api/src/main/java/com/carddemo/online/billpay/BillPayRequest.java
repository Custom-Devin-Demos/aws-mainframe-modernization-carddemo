package com.carddemo.online.billpay;

/** COBIL0AI input fields: ACTIDIN (Acct ID) and CONFIRM (Y/N). */
public record BillPayRequest(String acctId, String confirm) {
}
