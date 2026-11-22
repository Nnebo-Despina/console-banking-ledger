package com.team.banking.strategy;

import com.team.banking.model.Account;
import com.team.banking.model.CurrentAccount;

import java.math.BigDecimal;

/** Charges a configurable flat fee on current-account withdrawals after five free withdrawals. */
public final class CurrentAccountFeeStrategy implements WithdrawalFeeStrategy {
    private final int freeWithdrawals;
    private final BigDecimal flatFee;

    public CurrentAccountFeeStrategy(int freeWithdrawals, BigDecimal flatFee) {
        if (freeWithdrawals < 0) throw new IllegalArgumentException("Free withdrawal count cannot be negative.");
        if (flatFee == null || flatFee.signum() < 0 || flatFee.scale() > 2) {
            throw new IllegalArgumentException("Withdrawal fee must be a non-negative amount with at most two decimals.");
        }
        this.freeWithdrawals = freeWithdrawals;
        this.flatFee = flatFee;
    }

    @Override
    public BigDecimal calculateFee(Account account, int withdrawalsAlreadyThisMonth) {
        if (account instanceof CurrentAccount && withdrawalsAlreadyThisMonth >= freeWithdrawals) {
            return flatFee;
        }
        return BigDecimal.ZERO;
    }

    public int getFreeWithdrawals() { return freeWithdrawals; }
    public BigDecimal getFlatFee() { return flatFee; }
}
