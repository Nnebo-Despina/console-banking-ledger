
package com.team.banking.model;

import java.math.BigDecimal;
import java.util.UUID;

public class AccountFactory {

    public static Account createSavingsAccount(
            Customer customer,
            BigDecimal balance,
            BigDecimal dailyWithdrawalLimit,
            BigDecimal minimumBalance) {

        String accountNumber = UUID.randomUUID().toString();

        return new SavingsAccount(
                accountNumber,
                customer,
                balance,
                dailyWithdrawalLimit,
                minimumBalance
        );
    }

    public static Account createCurrentAccount(
            Customer customer,
            BigDecimal balance,
            BigDecimal dailyWithdrawalLimit,
            BigDecimal overdraftLimit) {

        String accountNumber = UUID.randomUUID().toString();

        return new CurrentAccount(
                accountNumber,
                customer,
                balance,
                dailyWithdrawalLimit,
                overdraftLimit
        );
    }
}
