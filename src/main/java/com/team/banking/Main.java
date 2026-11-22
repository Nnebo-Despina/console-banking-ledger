package com.team.banking;

import com.team.banking.cli.BankingCLI;
import com.team.banking.command.CommandParser;
import com.team.banking.model.Admin;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.TransactionRepository;
import com.team.banking.repository.UserRepository;
import com.team.banking.repository.json.JsonAccountRepository;
import com.team.banking.repository.json.JsonPersistenceException;
import com.team.banking.repository.json.JsonTransactionRepository;
import com.team.banking.repository.json.JsonUserRepository;
import com.team.banking.service.AccountService;
import com.team.banking.service.AdminService;
import com.team.banking.service.AuthenticationService;
import com.team.banking.service.CustomerService;
import com.team.banking.service.StatementService;
import com.team.banking.service.TransactionService;
import com.team.banking.service.TransferService;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

public class Main {
    private static final String DEFAULT_ADMIN_EMAIL = "admin@bank.local";
    private static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe123!";

    public static void main(String[] args) {
        try {
            Path data = Path.of("data");
            UserRepository users = new JsonUserRepository(data.resolve("users.json"));
            TransactionRepository ledger = new JsonTransactionRepository(data.resolve("transactions.json"));
            AccountRepository accountRepository = new JsonAccountRepository(
                    data.resolve("accounts.json"), users, ledger);

            AuthenticationService authentication = new AuthenticationService(users);
            bootstrapAdministrator(users, authentication);

            CustomerService customers = new CustomerService(users, authentication);
            TransactionService transactions = new TransactionService(
                    accountRepository, ledger, authentication, Clock.systemDefaultZone());
            AccountService accounts = new AccountService(
                    accountRepository, users, authentication, transactions);
            TransferService transfers = new TransferService(accounts, transactions, authentication);
            StatementService statements = new StatementService(accounts);
            AdminService admin = new AdminService(authentication, customers, accounts, ledger);

            CommandParser parser = new CommandParser(authentication, customers, accounts,
                    transactions, transfers, statements, admin, System.out);
            new BankingCLI(parser, System.out, System.err).run();
        } catch (JsonPersistenceException | UncheckedIOException ex) {
            System.err.println("Startup/persistence error: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static void bootstrapAdministrator(UserRepository users, AuthenticationService authentication) {
        boolean adminExists = users.findAll().stream().anyMatch(user -> user.getRole().name().equals("ADMIN"));
        if (adminExists) return;

        String email = System.getenv().getOrDefault("BANK_ADMIN_EMAIL", DEFAULT_ADMIN_EMAIL);
        String password = System.getenv().getOrDefault("BANK_ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);
        authentication.register(new Admin("ADMIN-" + UUID.randomUUID(), "System Administrator", email),
                password, true);
        System.out.println("A default administrator account has been created.");
        System.out.println("Admin email: " + email);
        if (System.getenv("BANK_ADMIN_PASSWORD") == null) {
            System.out.println("Temporary password: " + DEFAULT_ADMIN_PASSWORD);
        } else {
            System.out.println("Temporary password: supplied through BANK_ADMIN_PASSWORD.");
        }
        System.out.println("Sign in and run change-password before using admin commands.");
    }
}
