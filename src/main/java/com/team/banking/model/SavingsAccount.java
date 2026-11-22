package com.team.banking.model;

import java.math.BigDecimal;

public class SavingsAccount extends Account {
    private final BigDecimal minimumBalance;

    public SavingsAccount(String accountNumber, Customer owner, BigDecimal balance,
                          BigDecimal dailyWithdrawalLimit, BigDecimal minimumBalance) {
        super(accountNumber, owner, balance, dailyWithdrawalLimit);
        if (minimumBalance == null || minimumBalance.signum() < 0) {
            throw new IllegalArgumentException("Minimum balance cannot be negative.");
        }
        if (balance.compareTo(minimumBalance) < 0) {
            throw new IllegalArgumentException("Balance cannot be below the minimum balance.");
        }
        this.minimumBalance = minimumBalance;
    }

    public BigDecimal getMinimumBalance() { return minimumBalance; }

    @Override
    public void withdraw(BigDecimal amount) {
        ensureOpen();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
        }
        BigDecimal remaining = getBalance().subtract(amount);
        if (remaining.compareTo(minimumBalance) < 0) {
            throw new IllegalArgumentException("Insufficient funds. Minimum balance must be maintained.");
        }
        balance = remaining;
    }

    @Override
    public BigDecimal calculateInterest() {
        return balance.multiply(new BigDecimal("0.05"));
    }
}
