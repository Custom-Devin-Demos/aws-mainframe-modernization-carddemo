package com.carddemo.interest.repository;

import com.carddemo.interest.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Account}.
 *
 * <p>Mirrors the random-access {@code ACCOUNT-FILE} (ACCTFILE) reads/rewrites in
 * {@code app/cbl/CBACT04C.cbl} paragraphs {@code 1100-GET-ACCT-DATA} and
 * {@code 1050-UPDATE-ACCOUNT}.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
}
