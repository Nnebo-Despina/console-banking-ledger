
package com.team.banking.model;

import java.math.BigDecimal;

public class CurrentAccountFeeStrategy implements FeeStrategy {

    private BigDecimal fixedRate;
    private BigDecimal percentageRate;

    public CurrentAccountFeeStrategy(
            BigDecimal fixedRate,
            BigDecimal percentageRate) {

        this.fixedRate = fixedRate;
        this.percentageRate = percentageRate;
    }

    @Override
    public BigDecimal calculateFee(BigDecimal withdrawalAmount) {

        BigDecimal percentageFee =
                withdrawalAmount.multiply(percentageRate);

        return fixedRate.add(percentageFee);
    }
}
