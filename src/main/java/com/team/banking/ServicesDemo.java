package com.team.banking;

import com.team.banking.model.Account;
import com.team.banking.model.Customer;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.CustomerRepository;
import com.team.banking.repository.TransactionRepository;
import com.team.banking.repository.UserRepository;
import com.team.banking.repository.memory.InMemoryAccountRepository;
import com.team.banking.repository.memory.InMemoryCustomerRepository;
import com.team.banking.repository.memory.InMemoryTransactionRepository;
import com.team.banking.repository.memory.InMemoryUserRepository;
import com.team.banking.service.*;

import java.math.BigDecimal;
import java.time.Clock;

/** Throwaway file for testing the services by hand. Delete it before submitting. */
public class ServicesDemo {
    
    public static void main(String[] args) {
        // Repositories are in memory, so this demo starts with empty data each run.
        UserRepository userRepository = new InMemoryUserRepository();
        AccountRepository accountRepository = new InMemoryAccountRepository();
        TransactionRepository transactionRepository = new InMemoryTransactionRepository();

        // Build the services using the updated constructor dependencies.
        AuthenticationService authentication = new AuthenticationService(userRepository);
        CustomerService customers = new CustomerService(userRepository, authentication);
        TransactionService transactions = new TransactionService(
                accountRepository,
                transactionRepository,
                authentication,
                Clock.systemDefaultZone()
        );
        AccountService accounts = new AccountService(
                accountRepository,
                userRepository,
                authentication,
                transactions
        );
        TransferService transfers = new TransferService(accounts, transactions, authentication);
        StatementService statements = new StatementService(accounts);

        // Register and sign in a sample customer.
        Customer customer = customers.registerCustomer(
                "Demo Customer",
                "demo@example.com",
                "DemoPass123"
        );
        authentication.login(customer.getEmail(), "DemoPass123");

        // Open two accounts, deposit money, and transfer between them.
        Account savings = accounts.openSavingsAccount(
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                new BigDecimal("100.00")
        );
        Account current = accounts.openCurrentAccount(
                BigDecimal.ZERO,
                new BigDecimal("500.00"),
                new BigDecimal("500.00")
        );

        transactions.deposit(savings.getAccountNumber(), new BigDecimal("250.00"));
        transfers.transfer(
                savings.getAccountNumber(),
                current.getAccountNumber(),
                new BigDecimal("100.00")
        );

        System.out.println("Service-layer demo for " + customer.getFullname());
        System.out.println("Savings account: " + savings.getAccountNumber());
        System.out.println("Savings balance: " + accounts.getBalance(savings.getAccountNumber()));
        System.out.println("Current account: " + current.getAccountNumber());
        System.out.println("Current balance: " + accounts.getBalance(current.getAccountNumber()));
        System.out.println();
        System.out.println(statements.generateStatement(savings.getAccountNumber()));
    }

}
