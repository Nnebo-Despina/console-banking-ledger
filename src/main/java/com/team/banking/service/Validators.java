package com.team.banking.service;

import java.math.BigDecimal;

/**
 * Small checks shared by all services, so each rule is written exactly once.
 * Package-private: only classes inside the service package can use it.
 */
final class Validators {

    private Validators() {
        // Nobody should create a Validators object; it only holds static helpers.
    }

    static BigDecimal requirePositiveMoney(BigDecimal value, String label) {
        checkMoneyFormat(value, label);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
        return value;
    }

    static BigDecimal requireNonNegativeMoney(BigDecimal value, String label) {
        checkMoneyFormat(value, label);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
        return value;
    }

    static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private static void checkMoneyFormat(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (value.scale() > 2) {
            throw new IllegalArgumentException(label + " cannot have more than 2 decimal places.");
        }
    }
}
