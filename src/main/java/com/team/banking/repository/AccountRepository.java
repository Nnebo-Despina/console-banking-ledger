package com.team.banking.repository;

import com.team.banking.model.Account;

import java.util.List;
import java.util.Optional;

/**
 * What the services need from "the place where accounts are stored".
 */
public interface AccountRepository {

    void save(Account account);

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByOwnerId(String ownerId);
}
