package com.team.banking.model;

import java.math.BigDecimal;

public class CurrentAccount extends Account {
    
    private BigDecimal overdraftLimit;

    public CurrentAccount(String accountNumber, Customer owner, BigDecimal balance, BigDecimal dailyWithdrawalLimit, BigDecimal overdraftLimit) {
        super(accountNumber, owner, balance, dailyWithdrawalLimit);
        this.overdraftLimit = overdraftLimit;
    }

    public BigDecimal getOverdraftLimit() {
        return overdraftLimit;
    }

    @Override
    public void withdraw(BigDecimal amount) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid withdrawal amount.");
        }

        BigDecimal remainingBalance = balance.subtract(amount);

        if (remainingBalance.compareTo(overdraftLimit.negate()) < 0) {
            throw new IllegalArgumentException(" Overdraft limit exceeded.");
        }
        balance = remainingBalance;
    }

    @Override
    public BigDecimal calculateInterest() {
        // Current accounts typically do not earn interest
        return BigDecimal.ZERO;
    }
    
}
