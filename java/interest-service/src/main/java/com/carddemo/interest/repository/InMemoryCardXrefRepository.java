package com.carddemo.interest.repository;

import com.carddemo.interest.domain.CardXref;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Map-backed XREF-FILE alternate index on XREF-ACCT-ID. Like the VSAM AIX read (first matching
 * record wins), the first xref loaded for an account is the one returned.
 */
@Repository
public class InMemoryCardXrefRepository implements CardXrefRepository {

    private final Map<String, CardXref> byAcctId = new LinkedHashMap<>();

    public void load(Collection<CardXref> xrefs) {
        byAcctId.clear();
        xrefs.forEach(x -> byAcctId.putIfAbsent(x.acctId(), x));
    }

    @Override
    public Optional<CardXref> findByAcctId(String acctId) {
        return Optional.ofNullable(byAcctId.get(acctId));
    }
}
