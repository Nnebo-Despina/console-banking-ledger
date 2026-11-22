package com.team.banking;

import com.team.banking.model.Account;
import com.team.banking.model.Customer;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.TransactionRepository;
import com.team.banking.repository.UserRepository;
import com.team.banking.repository.memory.InMemoryAccountRepository;
import com.team.banking.repository.memory.InMemoryTransactionRepository;
import com.team.banking.repository.memory.InMemoryUserRepository;
import com.team.banking.service.AccountService;
import com.team.banking.service.AuthenticationService;
import com.team.banking.service.CustomerService;
import com.team.banking.service.StatementService;
import com.team.banking.service.TransactionService;
import com.team.banking.service.TransferService;

import java.math.BigDecimal;
import java.time.Clock;

/** Small in-memory service demonstration; all information is discarded when it exits. */
public final class ServicesDemo {
    private ServicesDemo() { }

    public static void main(String[] args) {
        UserRepository users = new InMemoryUserRepository();
        AccountRepository accountsRepository = new InMemoryAccountRepository();
        TransactionRepository ledger = new InMemoryTransactionRepository();

        AuthenticationService authentication = new AuthenticationService(users);
        CustomerService customers = new CustomerService(users, authentication);
        Clock clock = Clock.systemDefaultZone();
        TransactionService transactions = new TransactionService(accountsRepository, ledger, authentication, clock);
        AccountService accounts = new AccountService(accountsRepository, users, authentication, transactions);
        TransferService transfers = new TransferService(accounts, transactions, authentication);
        StatementService statements = new StatementService(accounts, clock);

        Customer customer = customers.registerCustomer("Demo Customer", "demo@example.com", "6428");
        authentication.login(customer.getEmail(), "6428");
        Account savings = accounts.openSavingsAccount(new BigDecimal("1000.00"),
                new BigDecimal("500.00"), new BigDecimal("100.00"));
        Account current = accounts.openCurrentAccount(BigDecimal.ZERO,
                new BigDecimal("500.00"), new BigDecimal("500.00"));
        transactions.deposit(savings.getAccountNumber(), new BigDecimal("250.00"));
        transfers.transfer(savings.getAccountNumber(), current.getAccountNumber(), new BigDecimal("100.00"));

        System.out.println("Service-layer demo for " + customer.getFullname());
        System.out.println("Savings account: " + savings.getAccountNumber());
        System.out.println("Savings balance: " + accounts.getBalance(savings.getAccountNumber()));
        System.out.println("Current account: " + current.getAccountNumber());
        System.out.println("Current balance: " + current.getBalance());
        System.out.println();
        System.out.println(statements.generateStatement(savings.getAccountNumber()));
    }
}
