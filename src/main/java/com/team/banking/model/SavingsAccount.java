package com.team.banking.model;

import java.math.BigDecimal;

public class SavingsAccount extends Account {

    private BigDecimal minimumBalance;

    public SavingsAccount(String accountNumber, Customer owner, BigDecimal balance, BigDecimal dailyWithdrawalLimit, BigDecimal minimumBalance) {
        super(accountNumber, owner, balance, dailyWithdrawalLimit);

        this.minimumBalance = minimumBalance;
    }
    public BigDecimal getMinimumBalance() {
        return minimumBalance;
    }
    @Override
    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid withdrawal amount.");
        }

        BigDecimal remainingBalance = balance.subtract(amount);

        if (amount.compareTo(getBalance()) > 0) {
            throw new IllegalArgumentException("Insufficient funds.");
        }
        if (remainingBalance.compareTo(minimumBalance) < 0) {
            throw new IllegalArgumentException("Insufficient funds. Minimum balance must be maintained.");
        }
        balance = remainingBalance;
    }
    @Override 
    public BigDecimal calculateInterest() {
        BigDecimal interestRate = new BigDecimal("0.05"); // 5% interest rate
        return balance.multiply(interestRate);
    }

    
}
