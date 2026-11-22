package com.team.banking.model;

import java.math.BigDecimal;
import java.util.UUID;

public final class AccountFactory {
    private AccountFactory() { }

    public static Account createSavingsAccount(Customer customer, BigDecimal balance,
                                               BigDecimal dailyWithdrawalLimit, BigDecimal minimumBalance) {
        return createSavingsAccount(UUID.randomUUID().toString(), customer, balance,
                dailyWithdrawalLimit, minimumBalance);
    }

    /** Rebuilds a savings account with its original persisted account number. */
    public static Account createSavingsAccount(String accountNumber, Customer customer,
                                               BigDecimal balance, BigDecimal dailyWithdrawalLimit,
                                               BigDecimal minimumBalance) {
        return new SavingsAccount(accountNumber, customer, balance, dailyWithdrawalLimit, minimumBalance);
    }

    public static Account createCurrentAccount(Customer customer, BigDecimal balance,
                                               BigDecimal dailyWithdrawalLimit, BigDecimal overdraftLimit) {
        return createCurrentAccount(UUID.randomUUID().toString(), customer, balance,
                dailyWithdrawalLimit, overdraftLimit);
    }

    /** Rebuilds a current account with its original persisted account number. */
    public static Account createCurrentAccount(String accountNumber, Customer customer,
                                               BigDecimal balance, BigDecimal dailyWithdrawalLimit,
                                               BigDecimal overdraftLimit) {
        return new CurrentAccount(accountNumber, customer, balance, dailyWithdrawalLimit, overdraftLimit);
    }
}
