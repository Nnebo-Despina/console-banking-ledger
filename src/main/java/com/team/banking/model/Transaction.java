
package com.team.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {

    private String transactionId;
    private String transactionType;
    private BigDecimal amount;
    private LocalDateTime transactionDate;

    public Transaction(
            String transactionId,
            String transactionType,
            BigDecimal amount,
            LocalDateTime transactionDate) {

        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.transactionDate = transactionDate;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }
}
