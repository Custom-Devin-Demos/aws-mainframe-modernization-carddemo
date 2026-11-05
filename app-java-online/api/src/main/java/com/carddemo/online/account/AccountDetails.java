package com.carddemo.online.account;

import java.math.BigDecimal;

/**
 * Output fields of the COACTVW / COACTUP maps: ACCTDAT record plus the CUSTDAT record reached through
 * the CXACAIX cross reference. {@code version} identifies the values fetched (ACUP-OLD-DETAILS) and must
 * be sent back on update so that 9700-CHECK-CHANGE-IN-REC can detect concurrent changes.
 */
public record AccountDetails(
        String acctId,
        String activeStatus,
        String openDate,
        String expirationDate,
        String reissueDate,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        BigDecimal currBal,
        BigDecimal currCycCredit,
        BigDecimal currCycDebit,
        String groupId,
        String cardNum,
        String custId,
        String ssn,
        String dob,
        String ficoScore,
        String firstName,
        String middleName,
        String lastName,
        String addrLine1,
        String addrLine2,
        String city,
        String state,
        String zip,
        String country,
        String phone1,
        String phone2,
        String govtId,
        String eftAccountId,
        String primaryCardHolder,
        String version) {
}
