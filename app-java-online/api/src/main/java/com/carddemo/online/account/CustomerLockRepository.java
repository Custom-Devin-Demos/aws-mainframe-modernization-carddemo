package com.carddemo.online.account;

import com.carddemo.online.domain.Customer;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** EXEC CICS READ FILE(CUSTDAT) UPDATE. */
interface CustomerLockRepository extends Repository<Customer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.custId = :custId")
    Optional<Customer> lockById(@Param("custId") Long custId);
}
