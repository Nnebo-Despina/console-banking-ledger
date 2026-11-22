package com.team.banking.command;

import com.team.banking.model.Account;
import com.team.banking.model.Customer;
import com.team.banking.model.Transaction;
import com.team.banking.model.User;
import com.team.banking.model.UserRole;
import com.team.banking.service.AccountService;
import com.team.banking.service.AdminService;
import com.team.banking.service.AuthenticationService;
import com.team.banking.service.CustomerService;
import com.team.banking.service.StatementService;
import com.team.banking.service.TransactionService;
import com.team.banking.service.TransferService;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Parses typed commands and exposes only commands permitted to the current role. */
public final class CommandParser {
    private static final Pattern WORD = Pattern.compile("\\G\\s*(?:\"([^\"]*)\"|([^\\s\"]+))(?=\\s|$)");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of("help", "change-password", "logout", "exit");

    private final Map<String, Definition> commands = new LinkedHashMap<>();
    private final AuthenticationService authentication;
    private final CustomerService customers;
    private final AccountService accounts;
    private final TransactionService transactions;
    private final TransferService transfers;
    private final StatementService statements;
    private final AdminService admin;
    private final PrintStream output;

    public CommandParser(AuthenticationService authentication, CustomerService customers,
                         AccountService accounts, TransactionService transactions,
                         TransferService transfers, StatementService statements,
                         AdminService admin, PrintStream output) {
        this.authentication = authentication;
        this.customers = customers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.transfers = transfers;
        this.statements = statements;
        this.admin = admin;
        this.output = output;

        action("help", Access.ANYONE, Set.of(0), args -> printHelp());
        add("register <email> \"<full name>\"", Access.GUEST, Set.of(2), (args, readPassword) -> {
            String password = readPassword.get();
            if (password == null) return false;
            customers.registerCustomer(args.get(1), args.get(0), password);
            output.println("Customer registered. Use login to sign in.");
            return true;
        });
        add("login <email>", Access.GUEST, Set.of(1), (args, readPassword) -> {
            String password = readPassword.get();
            if (password == null) return false;
            User user = authentication.login(args.get(0), password);
            output.println("Signed in as " + user.getFullname() + " (" + user.getRole() + ").");
            if (authentication.mustChangePassword()) {
                output.println("You must change your password before using other commands.");
            }
            return true;
        });
        action("logout", Access.AUTHENTICATED, Set.of(0), args -> {
            authentication.logout();
            output.println("Signed out.");
        });
        add("change-password", Access.AUTHENTICATED, Set.of(0), (args, readPassword) -> {
            String current = readPassword.get();
            if (current == null) return false;
            String next = readPassword.get();
            if (next == null) return false;
            authentication.changePassword(current, next);
            output.println("Password changed.");
            return true;
        });

        action("open-savings <initial-deposit> <daily-limit> <minimum-balance>", Access.CUSTOMER, Set.of(3),
                args -> printNewAccount(accounts.openSavingsAccount(money(args.get(0)), money(args.get(1)), money(args.get(2)))));
        action("open-current <initial-deposit> <daily-limit> <overdraft-limit>", Access.CUSTOMER, Set.of(3),
                args -> printNewAccount(accounts.openCurrentAccount(money(args.get(0)), money(args.get(1)), money(args.get(2)))));
        action("accounts", Access.CUSTOMER, Set.of(0), args -> printAccounts(accounts.getMyAccounts()));
        action("balance <account>", Access.CUSTOMER, Set.of(1),
                args -> output.println("Balance: " + accounts.getBalance(args.get(0)).toPlainString()));
        action("deposit <account> <amount>", Access.CUSTOMER, Set.of(2),
                args -> printTransaction("Deposited", transactions.deposit(args.get(0), money(args.get(1)))));
        action("withdraw <account> <amount>", Access.CUSTOMER, Set.of(2),
                args -> printTransaction("Withdrew", transactions.withdraw(args.get(0), money(args.get(1)))));
        action("transfer <from-account> <to-account> <amount>", Access.CUSTOMER, Set.of(3),
                args -> printTransaction("Transferred", transfers.transfer(args.get(0), args.get(1), money(args.get(2)))));
        action("statement <account> [<from-date> <to-date>]", Access.CUSTOMER, Set.of(1, 3), args -> {
            String statement = args.size() == 1 ? statements.generateStatement(args.get(0))
                    : statements.generateStatement(args.get(0), date(args.get(1)), date(args.get(2)));
            output.print(statement);
        });

        // Administrator commands.
        add("create-customer <email> \"<full name>\"", Access.ADMIN, Set.of(2), (args, readPassword) -> {
            String temporaryPassword = readPassword.get();
            if (temporaryPassword == null) return false;
            Customer customer = admin.createCustomer(args.get(1), args.get(0), temporaryPassword);
            output.println("Customer created: " + customer.getFullname() + " | ID: " + customer.getUserId());
            output.println("Ask the customer to sign in and change the temporary password.");
            return true;
        });
        action("customers", Access.ADMIN, Set.of(0), args -> printCustomers(admin.getCustomers()));
        action("customer <customer-id-or-email>", Access.ADMIN, Set.of(1), args -> printCustomer(admin.getCustomer(args.get(0))));
        action("accounts-for <customer-id>", Access.ADMIN, Set.of(1), args -> printAccounts(admin.getCustomerAccounts(args.get(0))));
        action("all-accounts", Access.ADMIN, Set.of(0), args -> printAccounts(admin.getAllAccounts()));
        action("open-savings-for <customer-id> <initial-deposit> <daily-limit> <minimum-balance>", Access.ADMIN, Set.of(4), args ->
                printNewAccount(admin.openSavingsForCustomer(args.get(0), money(args.get(1)), money(args.get(2)), money(args.get(3)))));
        action("open-current-for <customer-id> <initial-deposit> <daily-limit> <overdraft-limit>", Access.ADMIN, Set.of(4), args ->
                printNewAccount(admin.openCurrentForCustomer(args.get(0), money(args.get(1)), money(args.get(2)), money(args.get(3)))));
        action("unlock <email>", Access.ADMIN, Set.of(1), args -> {
            admin.unlockUser(args.get(0));
            output.println("User unlocked. They can try signing in again.");
        });
        add("set-password <email>", Access.ADMIN, Set.of(1), (args, readPassword) -> {
            String password = readPassword.get();
            if (password == null) return false;
            admin.resetPassword(args.get(0), password);
            output.println("Password reset. The user must change it after signing in.");
            return true;
        });
        action("close-account <account>", Access.ADMIN, Set.of(1), args -> {
            admin.closeAccount(args.get(0));
            output.println("Account closed.");
        });
        action("ledger", Access.ADMIN, Set.of(0), args -> printLedger(admin.getSystemLedger()));
        add("exit", Access.ANYONE, Set.of(0), (args, readPassword) -> false);
    }

    public Command parse(String line) {
        List<String> words = split(line == null ? "" : line.strip());
        if (words.isEmpty()) return readPassword -> true;
        String name = words.get(0).toLowerCase(Locale.ROOT);
        Definition definition = commands.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown command. Type help for available commands.");
        }
        List<String> args = List.copyOf(words.subList(1, words.size()));
        if (!definition.argumentCounts().contains(args.size())) {
            throw new IllegalArgumentException("Usage: " + definition.usage());
        }
        return readPassword -> {
            if (!isAvailable(definition.access())) {
                throw new IllegalStateException(accessMessage(definition.access()));
            }
            if (authentication.mustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(name)) {
                throw new IllegalStateException("Change your password first. Type change-password.");
            }
            return definition.operation().execute(args, readPassword);
        };
    }

    private String accessMessage(Access access) {
        return switch (access) {
            case CUSTOMER -> "You must be logged in as a customer to use this command.";
            case ADMIN -> "You must be logged in as an administrator to use this command.";
            case GUEST -> "Sign out before using this command.";
            case AUTHENTICATED -> "You must log in first.";
            case ANYONE -> "This command is unavailable.";
        };
    }

    private void action(String usage, Access access, Set<Integer> counts, Consumer<List<String>> action) {
        add(usage, access, counts, (args, readPassword) -> {
            action.accept(args);
            return true;
        });
    }

    private void add(String usage, Access access, Set<Integer> counts, Operation operation) {
        String name = usage.split(" ", 2)[0];
        commands.put(name, new Definition(usage, access, counts, operation));
    }

    private void printHelp() {
        User user = authentication.getCurrentUser();
        output.println(user == null ? "Guest commands" : user.getRole() + " commands");
        for (Definition definition : commands.values()) {
            if (isAvailable(definition.access())
                    && (!authentication.mustChangePassword()
                    || PASSWORD_CHANGE_ALLOWED.contains(definition.usage().split(" ", 2)[0]))) {
                output.println("  " + definition.usage());
            }
        }
    }

    private boolean isAvailable(Access access) {
        User user = authentication.getCurrentUser();
        return switch (access) {
            case ANYONE -> true;
            case GUEST -> user == null;
            case AUTHENTICATED -> user != null;
            case CUSTOMER -> user != null && user.getRole() == UserRole.CUSTOMER;
            case ADMIN -> user != null && user.getRole() == UserRole.ADMIN;
        };
    }

    private void printNewAccount(Account account) {
        output.println("Opened " + account.getClass().getSimpleName() + " " + account.getAccountNumber());
    }

    private void printAccounts(List<Account> accountList) {
        if (accountList.isEmpty()) {
            output.println("No accounts found.");
            return;
        }
        for (Account account : accountList) {
            output.printf("%s  %s  %s  %s%n", account.getAccountNumber(),
                    account.getClass().getSimpleName(), account.getBalance().toPlainString(),
                    account.isClosed() ? "CLOSED" : "ACTIVE");
        }
    }

    private void printCustomers(List<Customer> customerList) {
        if (customerList.isEmpty()) {
            output.println("No customers registered.");
            return;
        }
        for (Customer customer : customerList) {
            output.printf("%s  %s  %s%n", customer.getUserId(), customer.getFullname(), customer.getEmail());
        }
    }

    private void printCustomer(Customer customer) {
        output.println("Customer ID : " + customer.getUserId());
        output.println("Full name   : " + customer.getFullname());
        output.println("Email       : " + customer.getEmail());
        output.println("Accounts:");
        printAccounts(customer.getAccounts());
    }

    private void printLedger(List<Transaction> ledger) {
        if (ledger.isEmpty()) {
            output.println("The system ledger is empty.");
            return;
        }
        output.printf("%-19s %-38s %-18s %12s %12s  %s%n",
                "Date", "Account", "Type", "Amount", "Balance after", "Description / Reference");
        for (Transaction transaction : ledger) {
            output.printf("%-19s %-38s %-18s %12s %12s  %s / %s%n",
                    transaction.getTransactionDate(), transaction.getAccountNumber(),
                    transaction.getTransactionType(), transaction.getAmount().toPlainString(),
                    transaction.getBalanceAfter().toPlainString(), transaction.getDescription(),
                    transaction.getReferenceId());
        }
    }

    private void printTransaction(String action, Transaction transaction) {
        output.printf("%s %s (transaction %s)%n", action,
                transaction.getAmount().toPlainString(), transaction.getTransactionId());
    }

    private static List<String> split(String line) {
        List<String> words = new ArrayList<>();
        var matcher = WORD.matcher(line);
        int end = 0;
        while (matcher.find()) {
            words.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
            end = matcher.end();
        }
        if (end != line.length()) {
            throw new IllegalArgumentException("Use matching double quotes around arguments with spaces.");
        }
        return words;
    }

    private static BigDecimal money(String text) {
        if (!DECIMAL.matcher(text).matches()) {
            throw new IllegalArgumentException("Amounts must be decimal numbers, for example 125.50.");
        }
        return new BigDecimal(text);
    }

    private static LocalDate date(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Dates must use YYYY-MM-DD.");
        }
    }

    private enum Access { ANYONE, GUEST, AUTHENTICATED, CUSTOMER, ADMIN }
    private record Definition(String usage, Access access, Set<Integer> argumentCounts, Operation operation) { }
    @FunctionalInterface private interface Operation {
        boolean execute(List<String> args, Supplier<String> readPassword);
    }
}
