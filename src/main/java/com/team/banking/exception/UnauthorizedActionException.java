package com.team.banking.exception;

public class UnauthorizedActionException extends IllegalStateException {
    public UnauthorizedActionException(String message) { super(message); }
}
