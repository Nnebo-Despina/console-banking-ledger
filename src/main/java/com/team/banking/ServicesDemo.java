package com.team.banking;

import com.team.banking.model.Account;
import com.team.banking.model.Customer;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.CustomerRepository;
import com.team.banking.repository.memory.InMemoryAccountRepository;
import com.team.banking.repository.memory.InMemoryCustomerRepository;
import com.team.banking.service.*;

import java.math.BigDecimal;

/** Throwaway file for testing the services by hand. Delete it before submitting. */
public class ServicesDemo {

    public static void main(String[] args) {
        CustomerRepository customers = new InMemoryCustomerRepository();
        AccountRepository accounts = new InMemoryAccountRepository();

        AuthenticationService auth = new AuthenticationService(customers);
        CustomerService customerService = new CustomerService(customers, auth);
        AccountService accountService = new AccountService(accounts, customers, auth);
        TransactionService transactions = new TransactionService(accounts, accountService);
        TransferService transfers = new TransferService(accountService, transactions);
        StatementService statements = new StatementService(accountService);

        customerService.registerCustomer("Ada Obi", "ada@example.com", "secret123");
        customerService.registerCustomer("Chidi Eze", "chidi@example.com", "hunter22");

        auth.login("chidi@example.com", "hunter22");
        Account chidiAccount = accountService.openSavingsAccount(
                new BigDecimal("500.00"), new BigDecimal("1000.00"), new BigDecimal("50.00"));
        auth.logout();

        auth.login("ada@example.com", "secret123");
        Account savings = accountService.openSavingsAccount(
                new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("100.00"));
        Account current = accountService.openCurrentAccount(
                new BigDecimal("200.00"), new BigDecimal("300.00"), new BigDecimal("150.00"));

        transactions.deposit(savings.getAccountNumber(), new BigDecimal("250.50"));
        transactions.withdraw(savings.getAccountNumber(), new BigDecimal("100.00"));
        transfers.transfer(savings.getAccountNumber(), current.getAccountNumber(), new BigDecimal("75.00"));
        transfers.transfer(current.getAccountNumber(), chidiAccount.getAccountNumber(), new BigDecimal("300.00"));

        System.out.println(statements.generateStatement(savings.getAccountNumber()));
        System.out.println(statements.generateStatement(current.getAccountNumber()));

        tryIt("Overdraw savings", () -> transactions.withdraw(savings.getAccountNumber(), new BigDecimal("5000")));
        tryIt("Break daily limit", () -> transactions.withdraw(savings.getAccountNumber(), new BigDecimal("400")));
        tryIt("Read Chidi's account", () -> statements.generateStatement(chidiAccount.getAccountNumber()));
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("[UNEXPECTED SUCCESS] " + label);
        } catch (RuntimeException e) {
            System.out.println("[Blocked] " + label + " -> " + e.getMessage());
        }
    }
}
