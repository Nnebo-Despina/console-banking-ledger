package com.team.banking.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public abstract class Account {
    private final String accountNumber;
    private final Customer owner;
    protected volatile BigDecimal balance;
    private final BigDecimal dailyWithdrawalLimit;
    private final List<Transaction> transactions = new ArrayList<>();
    private volatile boolean closed;

    protected Account(String accountNumber, Customer owner, BigDecimal balance,
                      BigDecimal dailyWithdrawalLimit) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number is required.");
        }
        if (owner == null) {
            throw new IllegalArgumentException("Account owner is required.");
        }
        if (balance == null) {
            throw new IllegalArgumentException("Account balance is required.");
        }
        if (dailyWithdrawalLimit == null || dailyWithdrawalLimit.signum() <= 0) {
            throw new IllegalArgumentException("Daily withdrawal limit must be positive.");
        }
        this.accountNumber = accountNumber;
        this.owner = owner;
        this.balance = balance;
        this.dailyWithdrawalLimit = dailyWithdrawalLimit;
    }

    public String getAccountNumber() { return accountNumber; }
    public Customer getOwner() { return owner; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getDailyWithdrawalLimit() { return dailyWithdrawalLimit; }
    public List<Transaction> getTransactions() { return transactions; }
    public boolean isClosed() { return closed; }

    public void deposit(BigDecimal amount) {
        ensureOpen();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive.");
        }
        balance = balance.add(amount);
    }

    public void close() {
        if (closed) {
            throw new IllegalStateException("This account is already closed.");
        }
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("An account can be closed only when its balance is zero.");
        }
        closed = true;
    }

    public abstract void withdraw(BigDecimal amount);
    public abstract BigDecimal calculateInterest();

    protected final void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("This account is closed.");
        }
    }

    /** Used by the transaction coordinator to roll back an uncommitted operation. */
    public final void restoreBalanceAfterFailedCommit(BigDecimal previousBalance) {
        if (previousBalance == null) {
            throw new IllegalArgumentException("Previous balance is required.");
        }
        this.balance = previousBalance;
    }

    /** Used by JSON restoration; older account records default to open. */
    public final void restoreClosedState(boolean value) {
        this.closed = value;
    }
}
