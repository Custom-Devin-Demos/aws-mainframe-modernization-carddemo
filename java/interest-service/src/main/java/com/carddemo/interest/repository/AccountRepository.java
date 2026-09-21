package com.carddemo.interest.repository;

import com.carddemo.interest.domain.Account;
import java.util.Optional;

/** Keyed I-O access to ACCOUNT-FILE (VSAM KSDS, key = ACCT-ID). */
public interface AccountRepository {
    Optional<Account> findById(String acctId);

    /** REWRITE FD-ACCTFILE-REC FROM ACCOUNT-RECORD. */
    void rewrite(Account account);
}
