package com.team.banking.model;

import java.math.BigDecimal;

public class CurrentAccount extends Account {
    private final BigDecimal overdraftLimit;

    public CurrentAccount(String accountNumber, Customer owner, BigDecimal balance,
                          BigDecimal dailyWithdrawalLimit, BigDecimal overdraftLimit) {
        super(accountNumber, owner, balance, dailyWithdrawalLimit);
        if (overdraftLimit == null || overdraftLimit.signum() < 0) {
            throw new IllegalArgumentException("Overdraft limit cannot be negative.");
        }
        if (balance.compareTo(overdraftLimit.negate()) < 0) {
            throw new IllegalArgumentException("Balance cannot exceed the configured overdraft limit.");
        }
        this.overdraftLimit = overdraftLimit;
    }

    public BigDecimal getOverdraftLimit() { return overdraftLimit; }

    @Override
    public void withdraw(BigDecimal amount) {
        ensureOpen();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
        }
        BigDecimal remaining = getBalance().subtract(amount);
        if (remaining.compareTo(overdraftLimit.negate()) < 0) {
            throw new IllegalArgumentException("Overdraft limit exceeded.");
        }
        balance = remaining;
    }

    @Override
    public BigDecimal calculateInterest() {
        return BigDecimal.ZERO;
    }
}
