package com.team.banking.exception;

public class InsufficientFundsException extends IllegalArgumentException {
    public InsufficientFundsException(String message) { super(message); }
}
