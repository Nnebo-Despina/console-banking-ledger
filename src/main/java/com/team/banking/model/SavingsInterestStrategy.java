
package com.team.banking.model;

import java.math.BigDecimal;

public class SavingsInterestStrategy implements InterestStrategy {

    private BigDecimal interestRate;

    public SavingsInterestStrategy(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    @Override
    public BigDecimal calculateInterest(BigDecimal balance) {

        return balance.multiply(interestRate);
    }
}
