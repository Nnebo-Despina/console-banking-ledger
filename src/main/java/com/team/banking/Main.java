package com.team.banking;

import com.team.banking.cli.BankingCLI;
import com.team.banking.command.CommandParser;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.CustomerRepository;
import com.team.banking.repository.json.JsonAccountRepository;
import com.team.banking.repository.json.JsonCustomerRepository;
import com.team.banking.repository.json.JsonPersistenceException;
import com.team.banking.service.AccountService;
import com.team.banking.service.AuthenticationService;
import com.team.banking.service.CustomerService;
import com.team.banking.service.StatementService;
import com.team.banking.service.TransactionService;
import com.team.banking.service.TransferService;

import java.io.UncheckedIOException;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        try {
            Path data = Path.of("data");
            CustomerRepository customerRepository = new JsonCustomerRepository(data.resolve("users.json"));
            AccountRepository accountRepository = new JsonAccountRepository(
                    data.resolve("accounts.json"), data.resolve("transactions.json"), customerRepository);

            AuthenticationService authentication = new AuthenticationService(customerRepository);
            CustomerService customers = new CustomerService(customerRepository, authentication);
            AccountService accounts = new AccountService(accountRepository, customerRepository, authentication);
            TransactionService transactions = new TransactionService(accountRepository, accounts);
            TransferService transfers = new TransferService(accounts, transactions);
            StatementService statements = new StatementService(accounts);

            CommandParser parser = new CommandParser(
                    authentication, customers, accounts, transactions, transfers, statements, System.out);
            new BankingCLI(parser, System.out, System.err).run();
        } catch (JsonPersistenceException | UncheckedIOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
