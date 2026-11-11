package com.team.banking.model;

import java.math.BigDecimal;

public class InterestingStrategy {

    BigDecimal calculateInterest(BigDecimal balance) {
        BigDecimal interestRate = new BigDecimal("0.05"); // 5% interest rate
        return balance.multiply(interestRate);
    }
    
}
