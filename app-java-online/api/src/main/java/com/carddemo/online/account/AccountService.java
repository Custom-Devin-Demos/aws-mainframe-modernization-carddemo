package com.carddemo.online.account;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.domain.Account;
import com.carddemo.online.domain.AccountRepository;
import com.carddemo.online.domain.CardXref;
import com.carddemo.online.domain.CardXrefRepository;
import com.carddemo.online.domain.Customer;
import com.carddemo.online.domain.CustomerRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COACTVWC (account view) and COACTUPC (account update). */
@Service
public class AccountService {

    /** Which program's account-number edit applies (2210-EDIT-ACCOUNT vs 1210-EDIT-ACCOUNT). */
    public enum Mode {
        VIEW,
        UPDATE
    }

    private final CardXrefRepository xrefs;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final AccountLockRepository accountLocks;
    private final CustomerLockRepository customerLocks;
    private final EntityManager em;
    private final Clock clock;

    public AccountService(CardXrefRepository xrefs, AccountRepository accounts, CustomerRepository customers,
            AccountLockRepository accountLocks, CustomerLockRepository customerLocks, EntityManager em) {
        this.xrefs = xrefs;
        this.accounts = accounts;
        this.customers = customers;
        this.accountLocks = accountLocks;
        this.customerLocks = customerLocks;
        this.em = em;
        this.clock = Clock.systemDefaultZone();
    }

    /** ENTER on COACTVW, or ENTER with details not yet fetched on COACTUP: 9000-READ-ACCT. */
    @Transactional(readOnly = true)
    public ScreenResponse<AccountDetails> read(String acctIdInput, Mode mode) {
        long acctId = editAccountId(acctIdInput, mode);
        String acct11 = acct11(acctId);
        // 9200-GETCARDXREF-BYACCT (CXACAIX)
        CardXref xref = xrefs.findFirstByAcctIdOrderByCardNumAsc(acctId)
                .orElseThrow(() -> CardDemoException.notFound(AccountMessages.xrefNotFound(acct11), "acctId"));
        // 9300-GETACCTDATA-BYACCT
        Account account = accounts.findById(acctId)
                .orElseThrow(() -> CardDemoException.notFound(AccountMessages.accountNotFound(acct11), "acctId"));
        // 9400-GETCUSTDATA-BYCUST
        Customer customer = customers.findById(xref.getCustId()).orElseThrow(() -> CardDemoException
                .notFound(AccountMessages.customerNotFound(cust9(xref.getCustId())), "acctId")
                .withData(toDetails(account, null, xref)));
        AccountDetails details = toDetails(account, customer, xref);
        return mode == Mode.UPDATE
                ? ScreenResponse.of(details, AccountMessages.PROMPT_FOR_CHANGES)
                : ScreenResponse.of(details);
    }

    /**
     * COACTUPC ENTER on fetched details ({@code validateOnly}: 1200-EDIT-MAP-INPUTS) and PF5
     * (9600-WRITE-PROCESSING): edits, then READ UPDATE of ACCTDAT and CUSTDAT, change check and both REWRITEs
     * in one transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public ScreenResponse<AccountDetails> update(String acctIdInput, AccountUpdateRequest request,
            boolean validateOnly) {
        long acctId = editAccountId(acctIdInput, Mode.UPDATE);
        Account account = accountLocks.lockById(acctId)
                .orElseThrow(() -> CardDemoException.notFound(AccountMessages.LOCK_ACCT_FAILED, "acctId"));
        Optional<CardXref> xref = xrefs.findFirstByAcctIdOrderByCardNumAsc(acctId);
        Customer customer = xref.flatMap(x -> customerLocks.lockById(x.getCustId()))
                .orElseThrow(() -> CardDemoException.notFound(AccountMessages.LOCK_CUST_FAILED, "acctId"));
        AccountDetails current = toDetails(account, customer, xref.get());
        boolean stale = !current.version().equals(request.version());

        // 1205-COMPARE-OLD-NEW
        if (!stale && !changed(request, current)) {
            return ScreenResponse.of(current, AccountMessages.NO_CHANGES).withField("acctStatus");
        }
        AccountUpdateEdits.Result error = AccountUpdateEdits.run(request, LocalDate.now(clock));
        if (error != null) {
            throw CardDemoException.validation(error.message(), error.field());
        }
        if (validateOnly) {
            return ScreenResponse.of(null, AccountMessages.PROMPT_FOR_CONFIRMATION);
        }
        // 9700-CHECK-CHANGE-IN-REC
        if (stale) {
            throw CardDemoException.conflict(AccountMessages.DATA_CHANGED, "acctId").withData(current);
        }
        apply(request, account, customer);
        try {
            em.flush();
        } catch (DataAccessException | jakarta.persistence.PersistenceException ex) {
            throw CardDemoException.failure(AccountMessages.UPDATE_FAILED, "acctId");
        }
        return ScreenResponse.success(toDetails(account, customer, xref.get()), AccountMessages.UPDATE_SUCCESS);
    }

    /** 2210-EDIT-ACCOUNT (COACTVWC) / 1210-EDIT-ACCOUNT (COACTUPC). */
    static long editAccountId(String input, Mode mode) {
        String v = input == null ? "" : input;
        if (CobolText.isBlank(v) || CobolText.rtrim(v).equals("*")) {
            throw CardDemoException.validation(AccountMessages.NO_INPUT, "acctId");
        }
        String padded = v.length() >= 11 ? v.substring(0, 11) : v + " ".repeat(11 - v.length());
        boolean numeric = padded.chars().allMatch(Character::isDigit);
        if (!numeric || Long.parseLong(padded) == 0) {
            throw CardDemoException.validation(
                    mode == Mode.VIEW ? AccountMessages.VIEW_ACCT_INVALID : AccountMessages.UPDATE_ACCT_INVALID,
                    "acctId");
        }
        return Long.parseLong(padded);
    }

    /** 1205-COMPARE-OLD-NEW: true when any field differs from the fetched values. */
    static boolean changed(AccountUpdateRequest r, AccountDetails old) {
        String[] oldPhone1 = phoneParts(old.phone1());
        String[] oldPhone2 = phoneParts(old.phone2());
        return !(upper(in(r.acctStatus(), 1)).equals(upper(old.activeStatus()))
                && sameMoney(r.currBal(), old.currBal())
                && sameMoney(r.creditLimit(), old.creditLimit())
                && sameMoney(r.cashCreditLimit(), old.cashCreditLimit())
                && sameDate(r.openYear(), r.openMonth(), r.openDay(), old.openDate())
                && sameDate(r.expiryYear(), r.expiryMonth(), r.expiryDay(), old.expirationDate())
                && sameDate(r.reissueYear(), r.reissueMonth(), r.reissueDay(), old.reissueDate())
                && sameMoney(r.currCycCredit(), old.currCycCredit())
                && sameMoney(r.currCycDebit(), old.currCycDebit())
                && sameText(r.groupId(), 10, old.groupId())
                && sameText(r.firstName(), 25, old.firstName())
                && sameText(r.middleName(), 25, old.middleName())
                && sameText(r.lastName(), 25, old.lastName())
                && sameText(r.addrLine1(), 50, old.addrLine1())
                && sameText(r.addrLine2(), 50, old.addrLine2())
                && sameText(r.city(), 50, old.city())
                && sameText(r.state(), 2, old.state())
                && sameText(r.country(), 3, old.country())
                && sameText(r.zip(), 5, old.zip())
                && in(r.phone1a(), 3).equals(oldPhone1[0])
                && in(r.phone1b(), 3).equals(oldPhone1[1])
                && in(r.phone1c(), 4).equals(oldPhone1[2])
                && in(r.phone2a(), 3).equals(oldPhone2[0])
                && in(r.phone2b(), 3).equals(oldPhone2[1])
                && in(r.phone2c(), 4).equals(oldPhone2[2])
                && (in(r.ssn1(), 3) + in(r.ssn2(), 2) + in(r.ssn3(), 4)).equals(old.ssn())
                && sameText(r.govtId(), 20, old.govtId())
                && sameDate(r.dobYear(), r.dobMonth(), r.dobDay(), old.dob())
                && in(r.eftAccountId(), 10).equals(old.eftAccountId())
                && sameText(r.primaryCardHolder(), 1, old.primaryCardHolder())
                && in(r.ficoScore(), 3).equals(old.ficoScore()));
    }

    /** 9600-WRITE-PROCESSING: build ACCT-UPDATE-RECORD and CUST-UPDATE-RECORD. */
    private static void apply(AccountUpdateRequest r, Account a, Customer c) {
        a.setActiveStatus(in(r.acctStatus(), 1));
        a.setCurrBal(AccountUpdateEdits.numvalC(in(r.currBal(), AccountUpdateEdits.MONEY_LEN)));
        a.setCreditLimit(AccountUpdateEdits.numvalC(in(r.creditLimit(), AccountUpdateEdits.MONEY_LEN)));
        a.setCashCreditLimit(AccountUpdateEdits.numvalC(in(r.cashCreditLimit(), AccountUpdateEdits.MONEY_LEN)));
        a.setCurrCycCredit(AccountUpdateEdits.numvalC(in(r.currCycCredit(), AccountUpdateEdits.MONEY_LEN)));
        a.setCurrCycDebit(AccountUpdateEdits.numvalC(in(r.currCycDebit(), AccountUpdateEdits.MONEY_LEN)));
        a.setOpenDate(toDate(r.openYear(), r.openMonth(), r.openDay()));
        a.setExpirationDate(toDate(r.expiryYear(), r.expiryMonth(), r.expiryDay()));
        a.setReissueDate(toDate(r.reissueYear(), r.reissueMonth(), r.reissueDay()));
        a.setGroupId(in(r.groupId(), 10));

        c.setFirstName(in(r.firstName(), 25));
        c.setMiddleName(in(r.middleName(), 25));
        c.setLastName(in(r.lastName(), 25));
        c.setAddrLine1(in(r.addrLine1(), 50));
        c.setAddrLine2(in(r.addrLine2(), 50));
        c.setAddrLine3(in(r.city(), 50));
        c.setAddrStateCd(in(r.state(), 2));
        c.setAddrCountryCd(in(r.country(), 3));
        c.setAddrZip(in(r.zip(), 5));
        c.setPhoneNum1(phone(r.phone1a(), r.phone1b(), r.phone1c()));
        c.setPhoneNum2(phone(r.phone2a(), r.phone2b(), r.phone2c()));
        c.setSsn(in(r.ssn1(), 3) + in(r.ssn2(), 2) + in(r.ssn3(), 4));
        c.setGovtIssuedId(in(r.govtId(), 20));
        c.setDob(toDate(r.dobYear(), r.dobMonth(), r.dobDay()));
        c.setEftAccountId(in(r.eftAccountId(), 10));
        c.setPriCardHolderInd(in(r.primaryCardHolder(), 1));
        c.setFicoCreditScore(Integer.parseInt(in(r.ficoScore(), 3)));
    }

    static AccountDetails toDetails(Account a, Customer c, CardXref x) {
        String partial = String.join("|", a.getActiveStatus(), money(a.getCurrBal()), money(a.getCreditLimit()),
                money(a.getCashCreditLimit()), money(a.getCurrCycCredit()), money(a.getCurrCycDebit()),
                str(a.getOpenDate()), str(a.getExpirationDate()), str(a.getReissueDate()),
                nz(a.getGroupId()).toLowerCase(Locale.ROOT));
        if (c == null) {
            return new AccountDetails(acct11(a.getAcctId()), a.getActiveStatus(), str(a.getOpenDate()),
                    str(a.getExpirationDate()), str(a.getReissueDate()), a.getCreditLimit(), a.getCashCreditLimit(),
                    a.getCurrBal(), a.getCurrCycCredit(), a.getCurrCycDebit(), nz(a.getGroupId()), x.getCardNum(),
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null);
        }
        String fico = c.getFicoCreditScore() == null ? "000" : String.format("%03d", c.getFicoCreditScore());
        // Fields compared by 9700-CHECK-CHANGE-IN-REC (customer text compared in upper case).
        String version = sha256(String.join("|", partial, cust9(c.getCustId()),
                upper(c.getFirstName()), upper(c.getMiddleName()), upper(c.getLastName()),
                upper(c.getAddrLine1()), upper(c.getAddrLine2()), upper(c.getAddrLine3()),
                upper(c.getAddrStateCd()), upper(c.getAddrCountryCd()), upper(c.getAddrZip()),
                nz(c.getPhoneNum1()), nz(c.getPhoneNum2()), nz(c.getSsn()), upper(c.getGovtIssuedId()),
                str(c.getDob()), nz(c.getEftAccountId()), upper(c.getPriCardHolderInd()), fico));
        return new AccountDetails(acct11(a.getAcctId()), a.getActiveStatus(), str(a.getOpenDate()),
                str(a.getExpirationDate()), str(a.getReissueDate()), a.getCreditLimit(), a.getCashCreditLimit(),
                a.getCurrBal(), a.getCurrCycCredit(), a.getCurrCycDebit(), nz(a.getGroupId()), x.getCardNum(),
                cust9(c.getCustId()), nz(c.getSsn()), str(c.getDob()), fico, nz(c.getFirstName()),
                nz(c.getMiddleName()), nz(c.getLastName()), nz(c.getAddrLine1()), nz(c.getAddrLine2()),
                nz(c.getAddrLine3()), nz(c.getAddrStateCd()), nz(c.getAddrZip()), nz(c.getAddrCountryCd()),
                nz(c.getPhoneNum1()), nz(c.getPhoneNum2()), nz(c.getGovtIssuedId()), nz(c.getEftAccountId()),
                nz(c.getPriCardHolderInd()).trim(), version);
    }

    /** CUST-PHONE-NUM-1 '(aaa)bbb-cccc': parts at (2:3), (6:3), (10:4). */
    static String[] phoneParts(String phone) {
        String p = phone == null ? "" : phone;
        p = p.length() >= 15 ? p : p + " ".repeat(15 - p.length());
        return new String[] {
            in(p.substring(1, 4), 3), in(p.substring(5, 8), 3), in(p.substring(9, 13), 4)};
    }

    private static String phone(String a, String b, String c) {
        String pa = in(a, 3);
        String pb = in(b, 3);
        String pc = in(c, 4);
        if (pa.isEmpty() && pb.isEmpty() && pc.isEmpty()) {
            return "";
        }
        return "(" + pa + ")" + pb + "-" + pc;
    }

    private static boolean sameMoney(String input, BigDecimal old) {
        BigDecimal n = AccountUpdateEdits.numvalC(in(input, AccountUpdateEdits.MONEY_LEN));
        return n != null && old != null && n.compareTo(old) == 0;
    }

    private static boolean sameDate(String y, String m, String d, String old) {
        String oldParts = old == null ? "" : old.replace("-", "");
        return (in(y, 4) + in(m, 2) + in(d, 2)).equals(oldParts);
    }

    private static boolean sameText(String input, int len, String old) {
        return upper(in(input, len)).trim().equals(upper(old).trim());
    }

    private static LocalDate toDate(String y, String m, String d) {
        return LocalDate.of(Integer.parseInt(in(y, 4)), Integer.parseInt(in(m, 2).trim()),
                Integer.parseInt(in(d, 2).trim()));
    }

    private static String in(String value, int len) {
        return AccountUpdateEdits.in(value, len);
    }

    private static String upper(String v) {
        return nz(v).toUpperCase(Locale.ROOT);
    }

    private static String nz(String v) {
        return v == null ? "" : CobolText.rtrim(v);
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static String money(BigDecimal v) {
        return v == null ? "" : v.setScale(2).toPlainString();
    }

    static String acct11(long acctId) {
        return String.format("%011d", acctId);
    }

    static String cust9(long custId) {
        return String.format("%09d", custId);
    }

    private static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
