package com.carddemo.online.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The non-key field edits of COACTUPC 1200-EDIT-MAP-INPUTS (and the CSUTLDPY date paragraphs), run in the
 * COBOL order. As in COBOL, every edit runs, the first failing edit supplies the message
 * ({@code IF WS-RETURN-MSG-OFF}) and the cursor goes to the first flagged field in 3300-SETUP-SCREEN-ATTRS
 * order.
 */
final class AccountUpdateEdits {

    /** BMS field lengths of the COACTUP map (CACTUPAI). */
    static final int MONEY_LEN = 15;

    /** 3300-SETUP-SCREEN-ATTRS cursor precedence (request property names). */
    static final List<String> CURSOR_ORDER = List.of(
            "acctStatus", "openYear", "openMonth", "openDay", "creditLimit",
            "expiryYear", "expiryMonth", "expiryDay", "cashCreditLimit",
            "reissueYear", "reissueMonth", "reissueDay", "currBal", "currCycCredit", "currCycDebit",
            "ssn1", "ssn2", "ssn3", "dobYear", "dobMonth", "dobDay", "ficoScore",
            "firstName", "middleName", "lastName", "addrLine1", "state", "zip", "city", "country",
            "phone1a", "phone1b", "phone1c", "phone2a", "phone2b", "phone2c",
            "eftAccountId", "primaryCardHolder");

    /** FUNCTION TEST-NUMVAL-C: optional sign (leading or trailing, or CR/DB), optional '$', digits with commas. */
    private static final Pattern NUMVAL_C = Pattern.compile(
            "^\\s*([+-])?\\s*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]*)?|\\.[0-9]+)\\s*([+-]|CR|DB|cr|db)?\\s*$");

    record Result(String message, String field) {
    }

    private final AccountUpdateRequest r;
    private final LocalDate today;
    private final Set<String> flagged = new HashSet<>();
    private String message;

    private AccountUpdateEdits(AccountUpdateRequest request, LocalDate today) {
        this.r = request;
        this.today = today;
    }

    /** Returns the first error (message + cursor field) or {@code null} when every edit passes. */
    static Result run(AccountUpdateRequest request, LocalDate today) {
        AccountUpdateEdits e = new AccountUpdateEdits(request, today);
        e.editAll();
        if (e.message == null && e.flagged.isEmpty()) {
            return null;
        }
        String field = CURSOR_ORDER.stream().filter(e.flagged::contains).findFirst().orElse(null);
        return new Result(e.message, field);
    }

    private void editAll() {
        yesNo("Account Status", "acctStatus", in(r.acctStatus(), 1));
        date("Open Date", "openYear", "openMonth", "openDay", r.openYear(), r.openMonth(), r.openDay());
        signed9v2("Credit Limit", "creditLimit", in(r.creditLimit(), MONEY_LEN));
        date("Expiry Date", "expiryYear", "expiryMonth", "expiryDay", r.expiryYear(), r.expiryMonth(), r.expiryDay());
        signed9v2("Cash Credit Limit", "cashCreditLimit", in(r.cashCreditLimit(), MONEY_LEN));
        date("Reissue Date", "reissueYear", "reissueMonth", "reissueDay",
                r.reissueYear(), r.reissueMonth(), r.reissueDay());
        signed9v2("Current Balance", "currBal", in(r.currBal(), MONEY_LEN));
        signed9v2("Current Cycle Credit Limit", "currCycCredit", in(r.currCycCredit(), MONEY_LEN));
        signed9v2("Current Cycle Debit Limit", "currCycDebit", in(r.currCycDebit(), MONEY_LEN));
        ssn();
        // 'Date of Birth' + EDIT-DATE-OF-BIRTH when the date itself is valid
        if (date("Date of Birth", "dobYear", "dobMonth", "dobDay", r.dobYear(), r.dobMonth(), r.dobDay())) {
            LocalDate dob = LocalDate.of(Integer.parseInt(in(r.dobYear(), 4)),
                    Integer.parseInt(in(r.dobMonth(), 2).trim()), Integer.parseInt(in(r.dobDay(), 2).trim()));
            if (!today.isAfter(dob)) {
                fail("Date of Birth:cannot be in the future", "dobYear", "dobMonth", "dobDay");
            }
        }
        // 1245-EDIT-NUM-REQD then 1275-EDIT-FICO-SCORE
        String fico = in(r.ficoScore(), 3);
        if (numRequired("FICO Score", "ficoScore", fico, 3)) {
            int score = Integer.parseInt(fico);
            if (score < 300 || score > 850) {
                fail("FICO Score: should be between 300 and 850", "ficoScore");
            }
        }
        alphaRequired("First Name", "firstName", in(r.firstName(), 25), 25);
        alphaOptional("Middle Name", "middleName", in(r.middleName(), 25), 25);
        alphaRequired("Last Name", "lastName", in(r.lastName(), 25), 25);
        mandatory("Address Line 1", "addrLine1", in(r.addrLine1(), 50));
        String state = in(r.state(), 2);
        boolean stateOk = alphaRequired("State", "state", state, 2);
        if (stateOk && !LookupCodes.US_STATE_CODES.contains(state)) {
            // 1270-EDIT-US-STATE-CD
            fail("State: is not a valid state code", "state");
            stateOk = false;
        }
        String zip = in(r.zip(), 5);
        boolean zipOk = numRequired("Zip", "zip", zip, 5);
        alphaRequired("City", "city", in(r.city(), 50), 50);
        alphaRequired("Country", "country", in(r.country(), 3), 3);
        phone("Phone Number 1", "phone1", in(r.phone1a(), 3), in(r.phone1b(), 3), in(r.phone1c(), 4));
        phone("Phone Number 2", "phone2", in(r.phone2a(), 3), in(r.phone2b(), 3), in(r.phone2c(), 4));
        numRequired("EFT Account Id", "eftAccountId", in(r.eftAccountId(), 10), 10);
        yesNo("Primary Card Holder", "primaryCardHolder", in(r.primaryCardHolder(), 1));
        // 1280-EDIT-US-STATE-ZIP-CD
        if (stateOk && zipOk && !LookupCodes.US_STATE_ZIP2_COMBOS.contains(state + zip.substring(0, 2))) {
            fail("Invalid zip code for state", "state", "zip");
        }
    }

    private void fail(String text, String... fields) {
        if (message == null) {
            message = text;
        }
        flagged.addAll(List.of(fields));
    }

    /** 1215-EDIT-MANDATORY. */
    private boolean mandatory(String name, String field, String value) {
        if (value.isBlank()) {
            fail(name + " must be supplied.", field);
            return false;
        }
        return true;
    }

    /** 1220-EDIT-YESNO. */
    private void yesNo(String name, String field, String value) {
        if (value.isBlank() || value.equals("0")) {
            fail(name + " must be supplied.", field);
        } else if (!value.equals("Y") && !value.equals("N")) {
            fail(name + " must be Y or N.", field);
        }
    }

    /** 1225-EDIT-ALPHA-REQD. */
    private boolean alphaRequired(String name, String field, String value, int len) {
        if (value.isBlank()) {
            fail(name + " must be supplied.", field);
            return false;
        }
        return alphaOnly(name, field, value, len);
    }

    /** 1235-EDIT-ALPHA-OPT. */
    private void alphaOptional(String name, String field, String value, int len) {
        if (!value.isBlank()) {
            alphaOnly(name, field, value, len);
        }
    }

    /** INSPECT ... CONVERTING LIT-ALL-ALPHA-FROM TO SPACES: only letters and spaces may remain. */
    private boolean alphaOnly(String name, String field, String value, int len) {
        String part = pad(value, len);
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            boolean letter = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            if (!letter && c != ' ') {
                fail(name + " can have alphabets only.", field);
                return false;
            }
        }
        return true;
    }

    /** 1245-EDIT-NUM-REQD on the first {@code len} characters. */
    private boolean numRequired(String name, String field, String value, int len) {
        String part = pad(value, len);
        if (part.isBlank()) {
            fail(name + " must be supplied.", field);
            return false;
        }
        if (!part.chars().allMatch(Character::isDigit)) {
            fail(name + " must be all numeric.", field);
            return false;
        }
        if (Long.parseLong(part) == 0) {
            fail(name + " must not be zero.", field);
            return false;
        }
        return true;
    }

    /** 1250-EDIT-SIGNED-9V2. */
    private void signed9v2(String name, String field, String value) {
        if (value.isBlank()) {
            fail(name + " must be supplied.", field);
        } else if (numvalC(value) == null) {
            fail(name + " is not valid", field);
        }
    }

    /** 1260-EDIT-US-PHONE-NUM (the whole number is optional; each part is checked when any is given). */
    private void phone(String name, String prefix, String a, String b, String c) {
        if (a.isBlank() && b.isBlank() && c.isBlank()) {
            return;
        }
        String fa = prefix + "a";
        String fb = prefix + "b";
        String fc = prefix + "c";
        // EDIT-AREA-CODE
        if (a.isBlank()) {
            fail(name + ": Area code must be supplied.", fa);
        } else if (!isNumeric(pad(a, 3))) {
            fail(name + ": Area code must be A 3 digit number.", fa);
        } else if (Integer.parseInt(a) == 0) {
            fail(name + ": Area code cannot be zero", fa);
        } else if (!LookupCodes.GENERAL_PURPOSE_AREA_CODES.contains(a.trim())) {
            fail(name + ": Not valid North America general purpose area code", fa);
        }
        // EDIT-US-PHONE-PREFIX
        if (b.isBlank()) {
            fail(name + ": Prefix code must be supplied.", fb);
        } else if (!isNumeric(pad(b, 3))) {
            fail(name + ": Prefix code must be A 3 digit number.", fb);
        } else if (Integer.parseInt(b) == 0) {
            fail(name + ": Prefix code cannot be zero", fb);
        }
        // EDIT-US-PHONE-LINENUM
        if (c.isBlank()) {
            fail(name + ": Line number code must be supplied.", fc);
        } else if (!isNumeric(pad(c, 4))) {
            fail(name + ": Line number code must be A 4 digit number.", fc);
        } else if (Integer.parseInt(c) == 0) {
            fail(name + ": Line number code cannot be zero", fc);
        }
    }

    /** 1265-EDIT-US-SSN. */
    private void ssn() {
        String p1 = in(r.ssn1(), 3);
        if (numRequired("SSN: First 3 chars", "ssn1", p1, 3)) {
            int n = Integer.parseInt(p1);
            if (n == 0 || n == 666 || n >= 900) {
                fail("SSN: First 3 chars: should not be 000, 666, or between 900 and 999", "ssn1");
            }
        }
        numRequired("SSN 4th & 5th chars", "ssn2", in(r.ssn2(), 2), 2);
        numRequired("SSN Last 4 chars", "ssn3", in(r.ssn3(), 4), 4);
    }

    /** EDIT-DATE-CCYYMMDD (CSUTLDPY). Returns true when the date is valid. */
    private boolean date(String name, String fy, String fm, String fd, String yearIn, String monthIn, String dayIn) {
        String year = in(yearIn, 4);
        String month = in(monthIn, 2);
        String day = in(dayIn, 2);
        boolean ok = true;
        // EDIT-YEAR-CCYY
        Integer y = null;
        if (year.isBlank()) {
            fail(name + " : Year must be supplied.", fy);
            ok = false;
        } else if (!isNumeric(pad(year, 4))) {
            fail(name + " must be 4 digit number.", fy);
            ok = false;
        } else {
            y = Integer.parseInt(year);
            int cc = y / 100;
            if (cc != 19 && cc != 20) {
                fail(name + " : Century is not valid.", fy);
                ok = false;
            }
        }
        // EDIT-MONTH
        Integer m = null;
        if (month.isBlank()) {
            fail(name + " : Month must be supplied.", fm);
            ok = false;
        } else {
            Integer v = numval(month);
            if (v == null || v < 1 || v > 12) {
                fail(name + ": Month must be a number between 1 and 12.", fm);
                ok = false;
            }
            m = v;
        }
        // EDIT-DAY
        Integer d = null;
        if (day.isBlank()) {
            fail(name + " : Day must be supplied.", fd);
            ok = false;
        } else {
            Integer v = numval(day);
            if (v == null || v < 1 || v > 31) {
                fail(name + ":day must be a number between 1 and 31.", fd);
                ok = false;
            }
            d = v;
        }
        // EDIT-DAY-MONTH-YEAR
        if (m != null && d != null) {
            boolean month31 = m == 1 || m == 3 || m == 5 || m == 7 || m == 8 || m == 10 || m == 12;
            if (!month31 && d == 31) {
                fail(name + ":Cannot have 31 days in this month.", fd, fm);
                return false;
            }
            if (m == 2 && d == 30) {
                fail(name + ":Cannot have 30 days in this month.", fd, fm);
                return false;
            }
            if (m == 2 && d == 29 && y != null) {
                int divBy = y % 100 == 0 ? 400 : 4;
                if (y % divBy != 0) {
                    fail(name + ":Not a leap year.Cannot have 29 days in this month.", fd, fm, fy);
                    return false;
                }
            }
        }
        return ok;
    }

    /** FUNCTION NUMVAL-C, or {@code null} when TEST-NUMVAL-C fails or the value does not fit S9(10)V99. */
    static BigDecimal numvalC(String value) {
        if (value == null) {
            return null;
        }
        Matcher mt = NUMVAL_C.matcher(value);
        if (!mt.matches() || (mt.group(1) != null && mt.group(3) != null)) {
            return null;
        }
        BigDecimal n = new BigDecimal(mt.group(2).replace(",", "")).setScale(2, RoundingMode.DOWN);
        String sign = mt.group(1) != null ? mt.group(1) : mt.group(3);
        if (sign != null && (sign.equals("-") || sign.equalsIgnoreCase("CR") || sign.equalsIgnoreCase("DB"))) {
            n = n.negate();
        }
        if (n.abs().compareTo(new BigDecimal("9999999999.99")) > 0) {
            return null;
        }
        return n;
    }

    private static Integer numval(String value) {
        String v = value.trim();
        return isNumeric(v) ? Integer.valueOf(v) : null;
    }

    private static boolean isNumeric(String value) {
        return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }

    private static String pad(String value, int len) {
        return value.length() >= len ? value.substring(0, len) : value + " ".repeat(len - value.length());
    }

    /**
     * A received map field: {@code null} -> blank, cut to the BMS length, and '*' or spaces treated as blank
     * (1100-RECEIVE-MAP moves LOW-VALUES for those).
     */
    static String in(String value, int len) {
        if (value == null) {
            return "";
        }
        String v = value.length() > len ? value.substring(0, len) : value;
        String t = v.stripTrailing();
        return t.isBlank() || t.equals("*") ? "" : t;
    }
}
