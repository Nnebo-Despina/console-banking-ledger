package com.team.banking.exception;

public class AccountNotFoundException extends IllegalArgumentException {
    public AccountNotFoundException(String message) { super(message); }
}
