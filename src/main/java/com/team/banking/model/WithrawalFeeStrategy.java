package com.team.banking.model;

import java.math.BigDecimal;

public class WithrawalFeeStrategy {
    BigDecimal calculateFee(BigDecimal withdrawalAmount) {
        BigDecimal feeRate = new BigDecimal("0.02"); // 2% withdrawal fee
        return withdrawalAmount.multiply(feeRate);
    }
}
