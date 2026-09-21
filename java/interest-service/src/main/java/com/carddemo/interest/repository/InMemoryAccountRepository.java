package com.carddemo.interest.repository;

import com.carddemo.interest.domain.Account;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Map-backed ACCOUNT-FILE keyed by ACCT-ID; preserves load order for writing back. */
@Repository
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> byId = new LinkedHashMap<>();

    public void load(Collection<Account> accounts) {
        byId.clear();
        accounts.forEach(a -> byId.put(a.id(), a));
    }

    public List<Account> findAll() {
        return List.copyOf(byId.values());
    }

    @Override
    public Optional<Account> findById(String acctId) {
        return Optional.ofNullable(byId.get(acctId));
    }

    @Override
    public void rewrite(Account account) {
        if (!byId.containsKey(account.id())) {
            throw new IllegalStateException("REWRITE of unknown account " + account.id());
        }
        byId.put(account.id(), account);
    }
}
