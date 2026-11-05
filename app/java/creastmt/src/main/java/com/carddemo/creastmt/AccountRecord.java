package com.carddemo.creastmt;

import java.math.BigDecimal;

/** CVACT01Y ACCOUNT-RECORD (RECLN 300). Amounts are PIC S9(10)V99. */
public record AccountRecord(
        long acctId,
        String activeStatus,
        BigDecimal currBal,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        String openDate,
        String expirationDate,
        String reissueDate,
        BigDecimal currCycCredit,
        BigDecimal currCycDebit,
        String addrZip,
        String groupId) {

    public static final int LENGTH = 300;

    public static AccountRecord parse(String line) {
        String r = Cobol.pic(line, LENGTH);
        return new AccountRecord(
                Cobol.unsigned(Cobol.field(r, 0, 11)),
                Cobol.field(r, 11, 1),
                Cobol.zoned(Cobol.field(r, 12, 12), 2),
                Cobol.zoned(Cobol.field(r, 24, 12), 2),
                Cobol.zoned(Cobol.field(r, 36, 12), 2),
                Cobol.field(r, 48, 10),
                Cobol.field(r, 58, 10),
                Cobol.field(r, 68, 10),
                Cobol.zoned(Cobol.field(r, 78, 12), 2),
                Cobol.zoned(Cobol.field(r, 90, 12), 2),
                Cobol.field(r, 102, 10),
                Cobol.field(r, 112, 10));
    }
}
