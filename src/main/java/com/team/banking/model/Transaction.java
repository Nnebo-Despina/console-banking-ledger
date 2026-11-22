package com.team.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An immutable, append-only ledger entry. */
public final class Transaction {
    private final String transactionId;
    private final String transactionType;
    private final BigDecimal amount;
    private final LocalDateTime transactionDate;
    private final String accountNumber;
    private final BigDecimal balanceAfter;
    private final String description;
    private final String referenceId;

    public Transaction(String transactionId, String transactionType, BigDecimal amount,
                       LocalDateTime transactionDate, String accountNumber,
                       BigDecimal balanceAfter, String description, String referenceId) {
        if (transactionId == null || transactionId.isBlank()
                || transactionType == null || transactionType.isBlank()
                || amount == null || amount.signum() <= 0
                || transactionDate == null || accountNumber == null || accountNumber.isBlank()
                || balanceAfter == null) {
            throw new IllegalArgumentException("Transaction fields are invalid.");
        }
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.accountNumber = accountNumber;
        this.balanceAfter = balanceAfter;
        this.description = description == null ? "" : description;
        this.referenceId = referenceId == null ? "" : referenceId;
    }

    /** Legacy constructor retained for source compatibility with older code. */
    public Transaction(String transactionId, String transactionType, BigDecimal amount,
                       LocalDateTime transactionDate) {
        this(transactionId, transactionType, amount, transactionDate,
                "UNKNOWN", BigDecimal.ZERO, "", "");
    }

    public String getTransactionId() { return transactionId; }
    public String getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
    public String getAccountNumber() { return accountNumber; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public String getReferenceId() { return referenceId; }
}
