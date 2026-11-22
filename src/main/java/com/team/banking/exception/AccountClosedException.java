package com.team.banking.exception;

public class AccountClosedException extends IllegalStateException {
    public AccountClosedException(String message) { super(message); }
}
