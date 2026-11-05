package com.carddemo.creastmt;

/** CUSTREC CUSTOMER-RECORD (RECLN 500). */
public record CustomerRecord(
        long custId,
        String firstName,
        String middleName,
        String lastName,
        String addrLine1,
        String addrLine2,
        String addrLine3,
        String addrStateCd,
        String addrCountryCd,
        String addrZip,
        String phoneNum1,
        String phoneNum2,
        long ssn,
        String govtIssuedId,
        String dobYyyyMmDd,
        String eftAccountId,
        String priCardHolderInd,
        int ficoCreditScore) {

    public static final int LENGTH = 500;

    public static CustomerRecord parse(String line) {
        String r = Cobol.pic(line, LENGTH);
        return new CustomerRecord(
                Cobol.unsigned(Cobol.field(r, 0, 9)),
                Cobol.field(r, 9, 25),
                Cobol.field(r, 34, 25),
                Cobol.field(r, 59, 25),
                Cobol.field(r, 84, 50),
                Cobol.field(r, 134, 50),
                Cobol.field(r, 184, 50),
                Cobol.field(r, 234, 2),
                Cobol.field(r, 236, 3),
                Cobol.field(r, 239, 10),
                Cobol.field(r, 249, 15),
                Cobol.field(r, 264, 15),
                Cobol.unsigned(Cobol.field(r, 279, 9)),
                Cobol.field(r, 288, 20),
                Cobol.field(r, 308, 10),
                Cobol.field(r, 318, 10),
                Cobol.field(r, 328, 1),
                (int) Cobol.unsigned(Cobol.field(r, 329, 3)));
    }
}
