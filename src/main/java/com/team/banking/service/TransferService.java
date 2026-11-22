package com.team.banking.service;

import com.team.banking.model.Account;
import com.team.banking.model.Transaction;
import com.team.banking.model.UserRole;

import java.math.BigDecimal;

public class TransferService {
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AuthenticationService authenticationService;

    public TransferService(AccountService accountService, TransactionService transactionService,
                           AuthenticationService authenticationService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.authenticationService = authenticationService;
    }

    /** A customer may transfer from their own account to any existing account. */
    public Transaction transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        authenticationService.requireRole(UserRole.CUSTOMER);
        Validators.requirePositiveMoney(amount, "Transfer amount");
        String from = Validators.requireText(fromAccountNumber, "Source account number");
        String to = Validators.requireText(toAccountNumber, "Destination account number");
        if (from.equals(to)) throw new IllegalArgumentException("You cannot transfer to the same account.");
        Account source = accountService.getOwnedAccount(from);
        Account destination = accountService.getAccount(to);
        return transactionService.transfer(source, destination, amount);
    }
}
