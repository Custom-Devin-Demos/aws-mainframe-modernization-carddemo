package com.carddemo.interest.repository;

import com.carddemo.interest.model.Transaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Transaction}.
 *
 * <p>Mirrors the sequential write of the {@code TRANSACT-FILE} (TRANSACT) in
 * {@code app/cbl/CBACT04C.cbl} paragraph {@code 1300-B-WRITE-TX} (lines
 * 473-515). {@code save}/{@code saveAll} are inherited from
 * {@link JpaRepository}.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** Returns all transactions written for a given card number. */
    List<Transaction> findByCardNumber(String cardNumber);
}
