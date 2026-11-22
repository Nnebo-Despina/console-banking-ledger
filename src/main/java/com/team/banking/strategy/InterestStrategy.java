package com.team.banking.strategy;

import java.math.BigDecimal;

/** Calculates the interest to post for one monthly period. */
@FunctionalInterface
public interface InterestStrategy {
    BigDecimal calculateMonthlyInterest(BigDecimal balance);
}
