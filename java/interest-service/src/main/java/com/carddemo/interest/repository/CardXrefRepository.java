package com.carddemo.interest.repository;

import com.carddemo.interest.domain.CardXref;
import java.util.Optional;

/** Access to XREF-FILE via the alternate index on XREF-ACCT-ID (CBACT04C line 395). */
public interface CardXrefRepository {
    Optional<CardXref> findByAcctId(String acctId);
}
