package com.carddemo.online.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXrefRepository extends JpaRepository<CardXref, String> {

    /** CXACAIX alternate-index read: first xref for the account. */
    Optional<CardXref> findFirstByAcctIdOrderByCardNumAsc(Long acctId);

    List<CardXref> findByAcctIdOrderByCardNumAsc(Long acctId);
}
