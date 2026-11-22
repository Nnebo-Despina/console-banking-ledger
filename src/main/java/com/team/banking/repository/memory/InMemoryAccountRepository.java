package com.team.banking.repository.memory;

import com.team.banking.model.Account;
import com.team.banking.repository.AccountRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryAccountRepository implements AccountRepository {
    private final Map<String, Account> accountsByNumber = new HashMap<>();

    @Override public synchronized void save(Account account) {
        accountsByNumber.put(account.getAccountNumber(), account);
    }

    @Override public synchronized void saveAll(Collection<Account> accounts) {
        for (Account account : accounts) accountsByNumber.put(account.getAccountNumber(), account);
    }

    @Override public synchronized Optional<Account> findByAccountNumber(String accountNumber) {
        return Optional.ofNullable(accountsByNumber.get(accountNumber));
    }

    @Override public synchronized List<Account> findByOwnerId(String ownerId) {
        List<Account> result = new ArrayList<>();
        for (Account account : accountsByNumber.values()) {
            if (account.getOwner().getUserId().equals(ownerId)) result.add(account);
        }
        return result;
    }

    @Override public synchronized List<Account> findAll() { return new ArrayList<>(accountsByNumber.values()); }
}
