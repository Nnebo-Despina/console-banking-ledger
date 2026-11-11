
package com.team.banking.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public abstract class Account {

    private String accountNumber;
    private Customer owner;
    protected BigDecimal balance;
    private BigDecimal dailyWithdrawalLimit;

    private List<Transaction> transactions;

    public Account(String accountNumber, Customer owner,
                   BigDecimal balance, BigDecimal dailyWithdrawalLimit) {

        this.accountNumber = accountNumber;
        this.owner = owner;
        this.balance = balance;
        this.dailyWithdrawalLimit = dailyWithdrawalLimit;
        this.transactions = new ArrayList<>();
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Customer getOwner() {
        return owner;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getDailyWithdrawalLimit() {
        return dailyWithdrawalLimit;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void deposit(BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Deposit amount must be positive."
            );
        }

        balance = balance.add(amount);
    }

    public abstract void withdraw(BigDecimal amount);

    public abstract BigDecimal calculateInterest();
}
