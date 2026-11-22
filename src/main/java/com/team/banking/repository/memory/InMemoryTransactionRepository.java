package com.team.banking.repository.memory;

import com.team.banking.model.Transaction;
import com.team.banking.repository.TransactionRepository;

import java.util.ArrayList;
import java.util.List;

public class InMemoryTransactionRepository implements TransactionRepository {
    private final List<Transaction> transactions = new ArrayList<>();

    @Override public synchronized List<Transaction> findAll() { return List.copyOf(transactions); }

    @Override public synchronized List<Transaction> findByAccountNumber(String accountNumber) {
        return transactions.stream().filter(t -> t.getAccountNumber().equals(accountNumber)).toList();
    }

    @Override public synchronized void saveAll(List<Transaction> updated) {
        transactions.clear();
        transactions.addAll(updated);
    }
}
