package com.team.banking.exception;

public class DailyLimitExceededException extends IllegalArgumentException {
    public DailyLimitExceededException(String message) { super(message); }
}
