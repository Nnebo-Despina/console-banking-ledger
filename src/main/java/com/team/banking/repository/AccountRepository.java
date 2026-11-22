package com.team.banking.repository;

import com.team.banking.model.Account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    void save(Account account);
    /** Persist several account rows together as one logical account-file update. */
    default void saveAll(Collection<Account> accounts) {
        for (Account account : accounts) save(account);
    }
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByOwnerId(String ownerId);
    default List<Account> findAll() {
        return List.of();
    }
}
