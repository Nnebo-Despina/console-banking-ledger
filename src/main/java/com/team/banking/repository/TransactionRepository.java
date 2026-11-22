package com.team.banking.repository;

import com.team.banking.model.Transaction;

import java.util.List;

/** Stores the append-only system ledger independently of account records. */
public interface TransactionRepository {
    List<Transaction> findAll();
    List<Transaction> findByAccountNumber(String accountNumber);
    /** Replaces the complete committed ledger in one atomic file write. */
    void saveAll(List<Transaction> transactions);
}
