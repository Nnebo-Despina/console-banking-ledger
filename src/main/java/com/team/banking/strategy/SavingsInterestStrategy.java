package com.team.banking.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Savings interest is 2% per annum, posted monthly with half-up cent rounding. */
public final class SavingsInterestStrategy implements InterestStrategy {
    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.02");
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

    @Override
    public BigDecimal calculateMonthlyInterest(BigDecimal balance) {
        if (balance == null || balance.signum() < 0) {
            throw new IllegalArgumentException("A non-negative balance is required to calculate interest.");
        }
        return balance.multiply(ANNUAL_RATE)
                .divide(MONTHS_PER_YEAR, 2, RoundingMode.HALF_UP);
    }
}
