package com.team.banking.exception;

public class AccountLockedException extends IllegalStateException {
    public AccountLockedException(String message) { super(message); }
}
