package com.carddemo.online.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** STARTBR HIGH-VALUES + READPREV: the highest TRAN-ID, used to allocate the next id. */
    Optional<Transaction> findTopByOrderByTranIdDesc();
}
