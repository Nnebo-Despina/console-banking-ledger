package com.team.banking.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Customer extends User {
    private final List<Account> accounts = new ArrayList<>();

    public Customer(String userId, String fullname, String email) {
        super(userId, fullname, email, UserRole.CUSTOMER);
    }

    public synchronized void addAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null.");
        }
        if (account.getOwner() == null || !account.getOwner().getUserId().equals(getUserId())) {
            throw new IllegalArgumentException("Account belongs to another customer.");
        }
        boolean alreadyAdded = accounts.stream()
                .anyMatch(existing -> existing.getAccountNumber().equals(account.getAccountNumber()));
        if (!alreadyAdded) {
            accounts.add(account);
        }
    }

    public synchronized void removeAccount(String accountNumber) {
        accounts.removeIf(account -> account.getAccountNumber().equals(accountNumber));
    }

    public synchronized List<Account> getAccounts() {
        return Collections.unmodifiableList(new ArrayList<>(accounts));
    }
}
