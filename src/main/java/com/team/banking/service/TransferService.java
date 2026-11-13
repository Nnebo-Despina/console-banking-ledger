package com.team.banking.service;

import com.team.banking.model.Account;
import com.team.banking.model.Transaction;

import java.math.BigDecimal;

public class TransferService {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public TransferService(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    /** Moves money from one of MY accounts to any existing account. */
    public Transaction transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        Validators.requirePositiveMoney(amount, "Transfer amount");
        Validators.requireText(fromAccountNumber, "Source account number");
        Validators.requireText(toAccountNumber, "Destination account number");

        if (fromAccountNumber.trim().equals(toAccountNumber.trim())) {
            throw new IllegalArgumentException("You cannot transfer to the same account.");
        }

        Account source = accountService.getOwnedAccount(fromAccountNumber);
        Account destination = accountService.getAccount(toAccountNumber);

        transactionService.checkDailyLimit(source, amount);

        source.withdraw(amount);
        try {
            destination.deposit(amount);
        } catch (RuntimeException e) {
            source.deposit(amount); // undo the withdrawal so no money disappears
            throw e;
        }

        Transaction outgoing = transactionService.record(
                source, TransactionService.TYPE_TRANSFER_OUT, amount);
        transactionService.record(
                destination, TransactionService.TYPE_TRANSFER_IN, amount);
        return outgoing;
    }
}
