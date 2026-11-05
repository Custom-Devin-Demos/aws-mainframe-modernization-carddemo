package com.carddemo.online.account;

import com.carddemo.online.domain.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** EXEC CICS READ FILE(ACCTDAT) UPDATE. */
interface AccountLockRepository extends Repository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.acctId = :acctId")
    Optional<Account> lockById(@Param("acctId") Long acctId);
}
