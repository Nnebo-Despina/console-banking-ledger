package com.team.banking.exception;

public class TransactionNotReversibleException extends IllegalArgumentException {
    public TransactionNotReversibleException(String message) { super(message); }
}
