package com.team.banking.strategy;

import com.team.banking.model.Account;
import java.math.BigDecimal;

/** Selects a withdrawal fee based on the account and monthly withdrawal count. */
@FunctionalInterface
public interface WithdrawalFeeStrategy {
    BigDecimal calculateFee(Account account, int withdrawalsAlreadyThisMonth);
}
