package com.team.banking.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class Customer extends User {
   
    private final List<Account> accounts = new ArrayList<>();

    public Customer(String userId, String fullname, String email) {
        super(userId, fullname, email);

    }

    public void addAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }

        if (!account.getOwner().getUserId().equals(getUserId())) {
            throw new IllegalArgumentException("Account belongs to another customer.");
        }
        accounts.add(account);
    }
    public List<Account> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }
    
}
