package com.team.banking.service;

import com.team.banking.exception.InvalidAmountException;

import java.math.BigDecimal;

/** Shared validation rules for money and required text values. */
final class Validators {
    private Validators() { }

    static BigDecimal requirePositiveMoney(BigDecimal value, String label) {
        checkMoneyFormat(value, label);
        if (value.signum() <= 0) throw new InvalidAmountException(label + " must be greater than zero.");
        return value;
    }

    static BigDecimal requireNonNegativeMoney(BigDecimal value, String label) {
        checkMoneyFormat(value, label);
        if (value.signum() < 0) throw new InvalidAmountException(label + " cannot be negative.");
        return value;
    }

    static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private static void checkMoneyFormat(BigDecimal value, String label) {
        if (value == null) throw new InvalidAmountException(label + " is required.");
        if (value.scale() > 2) {
            throw new InvalidAmountException(label + " cannot have more than 2 decimal places.");
        }
    }
}
