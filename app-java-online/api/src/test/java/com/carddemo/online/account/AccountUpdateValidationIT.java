package com.carddemo.online.account;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every COACTUPC 1200-EDIT-MAP-INPUTS branch: exact WS-RETURN-MSG text and the field the cursor is put on by
 * 3300-SETUP-SCREEN-ATTRS. Each case starts from a request that passes every edit and overrides some fields.
 * Validation never writes, so the sample data is not reset.
 */
class AccountUpdateValidationIT extends AbstractAccountIT {

    /** @param paragraph COBOL paragraph / branch exercised */
    private static Arguments c(String paragraph, String message, String field, String... overrides) {
        return Arguments.of(paragraph, message, field, overrides);
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                // 1220-EDIT-YESNO 'Account Status'
                c("1220 blank", "Account Status must be supplied.", "acctStatus", "acctStatus", ""),
                c("1220 zeros", "Account Status must be supplied.", "acctStatus", "acctStatus", "0"),
                c("1220 not Y/N", "Account Status must be Y or N.", "acctStatus", "acctStatus", "X"),
                c("1220 lower case", "Account Status must be Y or N.", "acctStatus", "acctStatus", "y"),
                // EDIT-DATE-CCYYMMDD 'Open Date'
                c("EDIT-YEAR-CCYY blank", "Open Date : Year must be supplied.", "openYear", "openYear", ""),
                c("EDIT-YEAR-CCYY not numeric", "Open Date must be 4 digit number.", "openYear", "openYear", "20AB"),
                c("EDIT-YEAR-CCYY short", "Open Date must be 4 digit number.", "openYear", "openYear", "201"),
                c("EDIT-YEAR-CCYY century", "Open Date : Century is not valid.", "openYear", "openYear", "1899"),
                c("EDIT-MONTH blank", "Open Date : Month must be supplied.", "openMonth", "openMonth", ""),
                c("EDIT-MONTH range", "Open Date: Month must be a number between 1 and 12.", "openMonth",
                        "openMonth", "13"),
                c("EDIT-MONTH not numeric", "Open Date: Month must be a number between 1 and 12.", "openMonth",
                        "openMonth", "AB"),
                c("EDIT-DAY blank", "Open Date : Day must be supplied.", "openDay", "openDay", ""),
                c("EDIT-DAY range", "Open Date:day must be a number between 1 and 31.", "openDay", "openDay", "32"),
                c("EDIT-DAY not numeric", "Open Date:day must be a number between 1 and 31.", "openDay",
                        "openDay", "X1"),
                c("EDIT-DAY-MONTH-YEAR 31", "Open Date:Cannot have 31 days in this month.", "openMonth",
                        "openMonth", "04", "openDay", "31"),
                c("EDIT-DAY-MONTH-YEAR Feb 30", "Open Date:Cannot have 30 days in this month.", "openMonth",
                        "openMonth", "02", "openDay", "30"),
                c("EDIT-DAY-MONTH-YEAR not leap", "Open Date:Not a leap year.Cannot have 29 days in this month.",
                        "openYear", "openYear", "2023", "openMonth", "02", "openDay", "29"),
                c("EDIT-DAY-MONTH-YEAR century not leap",
                        "Open Date:Not a leap year.Cannot have 29 days in this month.", "openYear",
                        "openYear", "1900", "openMonth", "02", "openDay", "29"),
                // 1250-EDIT-SIGNED-9V2 'Credit Limit'
                c("1250 blank", "Credit Limit must be supplied.", "creditLimit", "creditLimit", ""),
                c("1250 TEST-NUMVAL-C", "Credit Limit is not valid", "creditLimit", "creditLimit", "12A"),
                c("1250 two signs", "Credit Limit is not valid", "creditLimit", "creditLimit", "-12-"),
                // 'Expiry Date'
                c("EDIT-YEAR-CCYY expiry", "Expiry Date : Year must be supplied.", "expiryYear", "expiryYear", ""),
                c("EDIT-MONTH expiry", "Expiry Date: Month must be a number between 1 and 12.", "expiryMonth",
                        "expiryMonth", "00"),
                c("EDIT-DAY expiry", "Expiry Date : Day must be supplied.", "expiryDay", "expiryDay", ""),
                // 'Cash Credit Limit'
                c("1250 cash blank", "Cash Credit Limit must be supplied.", "cashCreditLimit",
                        "cashCreditLimit", ""),
                c("1250 cash invalid", "Cash Credit Limit is not valid", "cashCreditLimit", "cashCreditLimit", "1.2.3"),
                // 'Reissue Date'
                c("EDIT-YEAR-CCYY reissue", "Reissue Date : Century is not valid.", "reissueYear",
                        "reissueYear", "2125"),
                c("EDIT-MONTH reissue", "Reissue Date : Month must be supplied.", "reissueMonth", "reissueMonth", ""),
                c("EDIT-DAY reissue", "Reissue Date:day must be a number between 1 and 31.", "reissueDay",
                        "reissueDay", "00"),
                // 'Current Balance', 'Current Cycle Credit Limit', 'Current Cycle Debit Limit'
                c("1250 balance blank", "Current Balance must be supplied.", "currBal", "currBal", ""),
                c("1250 balance invalid", "Current Balance is not valid", "currBal", "currBal", "abc"),
                c("1250 cycle credit", "Current Cycle Credit Limit must be supplied.", "currCycCredit",
                        "currCycCredit", ""),
                c("1250 cycle debit", "Current Cycle Debit Limit is not valid", "currCycDebit",
                        "currCycDebit", "1O0"),
                // 1265-EDIT-US-SSN
                c("1265 part1 blank", "SSN: First 3 chars must be supplied.", "ssn1", "ssn1", ""),
                c("1265 part1 not numeric", "SSN: First 3 chars must be all numeric.", "ssn1", "ssn1", "12A"),
                c("1265 part1 zero", "SSN: First 3 chars must not be zero.", "ssn1", "ssn1", "000"),
                c("1265 part1 666", "SSN: First 3 chars: should not be 000, 666, or between 900 and 999", "ssn1",
                        "ssn1", "666"),
                c("1265 part1 9xx", "SSN: First 3 chars: should not be 000, 666, or between 900 and 999", "ssn1",
                        "ssn1", "950"),
                c("1265 part2 blank", "SSN 4th & 5th chars must be supplied.", "ssn2", "ssn2", ""),
                c("1265 part2 zero", "SSN 4th & 5th chars must not be zero.", "ssn2", "ssn2", "00"),
                c("1265 part3 short", "SSN Last 4 chars must be all numeric.", "ssn3", "ssn3", "12"),
                // 'Date of Birth' + EDIT-DATE-OF-BIRTH
                c("EDIT-YEAR-CCYY dob", "Date of Birth : Year must be supplied.", "dobYear", "dobYear", ""),
                c("EDIT-MONTH dob", "Date of Birth: Month must be a number between 1 and 12.", "dobMonth",
                        "dobMonth", "13"),
                c("EDIT-DAY dob", "Date of Birth : Day must be supplied.", "dobDay", "dobDay", ""),
                c("EDIT-DATE-OF-BIRTH future", "Date of Birth:cannot be in the future", "dobYear",
                        "dobYear", "2099", "dobMonth", "01", "dobDay", "01"),
                // 1245-EDIT-NUM-REQD + 1275-EDIT-FICO-SCORE
                c("1245 fico blank", "FICO Score must be supplied.", "ficoScore", "ficoScore", ""),
                c("1245 fico not numeric", "FICO Score must be all numeric.", "ficoScore", "ficoScore", "7A0"),
                c("1245 fico zero", "FICO Score must not be zero.", "ficoScore", "ficoScore", "000"),
                c("1275 fico low", "FICO Score: should be between 300 and 850", "ficoScore", "ficoScore", "299"),
                c("1275 fico high", "FICO Score: should be between 300 and 850", "ficoScore", "ficoScore", "851"),
                // 1225-EDIT-ALPHA-REQD / 1235-EDIT-ALPHA-OPT names
                c("1225 first blank", "First Name must be supplied.", "firstName", "firstName", ""),
                c("1225 first alpha", "First Name can have alphabets only.", "firstName", "firstName", "J0hn"),
                c("1235 middle alpha", "Middle Name can have alphabets only.", "middleName", "middleName", "M."),
                c("1225 last blank", "Last Name must be supplied.", "lastName", "lastName", "   "),
                c("1225 last alpha", "Last Name can have alphabets only.", "lastName", "lastName", "O'Brien"),
                // 1215-EDIT-MANDATORY
                c("1215 address", "Address Line 1 must be supplied.", "addrLine1", "addrLine1", ""),
                // 'State' 1225 + 1270-EDIT-US-STATE-CD
                c("1225 state blank", "State must be supplied.", "state", "state", ""),
                c("1225 state alpha", "State can have alphabets only.", "state", "state", "N1"),
                c("1270 state code", "State: is not a valid state code", "state", "state", "XX"),
                // 'Zip' 1245
                c("1245 zip blank", "Zip must be supplied.", "zip", "zip", ""),
                c("1245 zip not numeric", "Zip must be all numeric.", "zip", "zip", "27A01"),
                c("1245 zip zero", "Zip must not be zero.", "zip", "zip", "00000"),
                // 'City', 'Country' 1225
                c("1225 city blank", "City must be supplied.", "city", "city", ""),
                c("1225 city alpha", "City can have alphabets only.", "city", "city", "St. Louis"),
                c("1225 country blank", "Country must be supplied.", "country", "country", ""),
                c("1225 country alpha", "Country can have alphabets only.", "country", "country", "US1"),
                // 1260-EDIT-US-PHONE-NUM
                c("EDIT-AREA-CODE blank", "Phone Number 1: Area code must be supplied.", "phone1a", "phone1a", ""),
                c("EDIT-AREA-CODE numeric", "Phone Number 1: Area code must be A 3 digit number.", "phone1a",
                        "phone1a", "9A8"),
                c("EDIT-AREA-CODE zero", "Phone Number 1: Area code cannot be zero", "phone1a", "phone1a", "000"),
                c("EDIT-AREA-CODE lookup", "Phone Number 1: Not valid North America general purpose area code",
                        "phone1a", "phone1a", "373"),
                c("EDIT-US-PHONE-PREFIX blank", "Phone Number 1: Prefix code must be supplied.", "phone1b",
                        "phone1b", ""),
                c("EDIT-US-PHONE-PREFIX numeric", "Phone Number 1: Prefix code must be A 3 digit number.",
                        "phone1b", "phone1b", "1A9"),
                c("EDIT-US-PHONE-PREFIX zero", "Phone Number 1: Prefix code cannot be zero", "phone1b",
                        "phone1b", "000"),
                c("EDIT-US-PHONE-LINENUM blank", "Phone Number 1: Line number code must be supplied.", "phone1c",
                        "phone1c", ""),
                c("EDIT-US-PHONE-LINENUM numeric", "Phone Number 1: Line number code must be A 4 digit number.",
                        "phone1c", "phone1c", "83A0"),
                c("EDIT-US-PHONE-LINENUM zero", "Phone Number 1: Line number code cannot be zero", "phone1c",
                        "phone1c", "0000"),
                c("1260 phone 2", "Phone Number 2: Not valid North America general purpose area code", "phone2a",
                        "phone2a", "373"),
                c("1260 phone 2 prefix", "Phone Number 2: Prefix code must be supplied.", "phone2b",
                        "phone2b", ""),
                // 'EFT Account Id' 1245
                c("1245 eft blank", "EFT Account Id must be supplied.", "eftAccountId", "eftAccountId", ""),
                c("1245 eft short", "EFT Account Id must be all numeric.", "eftAccountId", "eftAccountId", "12345"),
                c("1245 eft zero", "EFT Account Id must not be zero.", "eftAccountId", "eftAccountId", "0000000000"),
                // 'Primary Card Holder' 1220
                c("1220 primary blank", "Primary Card Holder must be supplied.", "primaryCardHolder",
                        "primaryCardHolder", ""),
                c("1220 primary invalid", "Primary Card Holder must be Y or N.", "primaryCardHolder",
                        "primaryCardHolder", "X"),
                // 1280-EDIT-US-STATE-ZIP-CD (flags state and zip; cursor goes to state)
                c("1280 state/zip", "Invalid zip code for state", "state", "zip", "10001"),
                // first edit supplies the message, 3300 order picks the cursor
                c("order: open date before credit limit", "Open Date : Day must be supplied.", "openDay",
                        "creditLimit", "", "openDay", ""),
                c("order: message from first edit, cursor on first flagged field",
                        "Credit Limit must be supplied.", "creditLimit", "creditLimit", "", "firstName", "",
                        "primaryCardHolder", "Q"),
                c("order: '*' is treated as blank", "Account Status must be supplied.", "acctStatus",
                        "acctStatus", "*", "lastName", "*"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalid")
    void rejects(String paragraph, String message, String field, String[] overrides) throws Exception {
        Map<String, Object> req = validRequest();
        for (int i = 0; i < overrides.length; i += 2) {
            req.put(overrides[i], overrides[i + 1]);
        }
        putAccount(ACCT_1, req, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value(field))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    static Stream<Arguments> valid() {
        return Stream.of(
                c("leap day", null, null, "openYear", "2000", "openMonth", "02", "openDay", "29"),
                c("1235 middle name optional", null, null, "middleName", ""),
                c("1260 phone optional", null, null, "phone2a", "", "phone2b", "", "phone2c", ""),
                c("NUMVAL-C formats", null, null, "creditLimit", "$1,234.56", "currBal", "12.34-",
                        "cashCreditLimit", "100CR"),
                c("single digit month/day", null, null, "openMonth", "3", "openDay", "7"),
                c("names with spaces", null, null, "firstName", "Mary Ann", "city", "New York", "state", "NY",
                        "zip", "10001"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("valid")
    void accepts(String paragraph, String message, String field, String[] overrides) throws Exception {
        Map<String, Object> req = validRequest();
        for (int i = 0; i < overrides.length; i += 2) {
            req.put(overrides[i], overrides[i + 1]);
        }
        putAccount(ACCT_1, req, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changes validated.Press F5 to save"));
    }
}
