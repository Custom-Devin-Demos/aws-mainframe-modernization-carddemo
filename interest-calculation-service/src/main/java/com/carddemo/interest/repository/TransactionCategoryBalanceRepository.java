package com.carddemo.interest.repository;

import com.carddemo.interest.model.TransactionCategoryBalance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link TransactionCategoryBalance}.
 *
 * <p>Mirrors the sequential read of the {@code TCATBAL-FILE} (TCATBALF) driver
 * file in {@code app/cbl/CBACT04C.cbl} paragraph {@code 1000-TCATBALF-GET-NEXT}.
 * The COBOL file is an INDEXED file accessed SEQUENTIALLY, so records arrive in
 * ascending key order (account id, then type code, then category code); the
 * {@code findAllByOrderByAccountId} method reproduces that ordering so the main
 * loop's account-grouping logic behaves identically.
 */
@Repository
public interface TransactionCategoryBalanceRepository
        extends JpaRepository<TransactionCategoryBalance, TransactionCategoryBalance.Key> {

    /**
     * Returns all category balances ordered by account id, mirroring the COBOL
     * sequential read of the TCATBALF file.
     */
    List<TransactionCategoryBalance> findAllByOrderByAccountId();
}
