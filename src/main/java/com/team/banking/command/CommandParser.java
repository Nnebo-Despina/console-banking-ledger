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
    private static final Set<String> PIN_CHANGE_ALLOWED = Set.of("help", "change-pin", "logout", "exit");

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
        add("register <email> \"<full name>\"", Access.GUEST, Set.of(2), (args, readPin) -> {
            String pin = readPin.get();
            if (pin == null) return false;
            customers.registerCustomer(args.get(1), args.get(0), pin);
            output.println("Customer registered. Use login and your new PIN to sign in.");
            return true;
        });
        add("login <user-id-or-email>", Access.GUEST, Set.of(1), (args, readPin) -> {
            String pin = readPin.get();
            if (pin == null) return false;
            User user = authentication.login(args.get(0), pin);
            output.println("Signed in as " + user.getFullname() + " (" + user.getRole() + ").");
            if (authentication.mustChangePin()) {
                output.println("You must change your temporary PIN before using other commands.");
            }
            return true;
        });
        action("logout", Access.AUTHENTICATED, Set.of(0), args -> {
            authentication.logout();
            output.println("Signed out.");
        });
        add("change-pin", Access.AUTHENTICATED, Set.of(0), (args, readPin) -> {
            String current = readPin.get();
            if (current == null) return false;
            String next = readPin.get();
            if (next == null) return false;
            authentication.changePin(current, next);
            output.println("PIN changed successfully.");
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
        action("withdraw <account> <amount>", Access.CUSTOMER, Set.of(2), args -> {
            printTransaction("Withdrew", transactions.withdraw(args.get(0), money(args.get(1))));
            output.println("Any applicable current-account fee is stored as a separate FEE ledger entry.");
        });
        action("transfer <from-account> <to-account> <amount>", Access.CUSTOMER, Set.of(3),
                args -> printTransaction("Transferred", transfers.transfer(args.get(0), args.get(1), money(args.get(2)))));
        action("statement <account> [<from-date> <to-date>]", Access.CUSTOMER, Set.of(1, 3), args -> {
            String statement = args.size() == 1 ? statements.generateStatement(args.get(0))
                    : statements.generateStatement(args.get(0), date(args.get(1)), date(args.get(2)));
            output.print(statement);
        });
        action("transactions <account> [<type|->] [<min|->] [<max|->] [<from-date> <to-date>]",
                Access.CUSTOMER, Set.of(1, 2, 3, 4, 6), args -> {
                    String type = args.size() >= 2 ? args.get(1) : null;
                    BigDecimal minimum = args.size() >= 3 ? optionalMoney(args.get(2)) : null;
                    BigDecimal maximum = args.size() >= 4 ? optionalMoney(args.get(3)) : null;
                    LocalDate from = args.size() == 6 ? date(args.get(4)) : null;
                    LocalDate to = args.size() == 6 ? date(args.get(5)) : null;
                    printTransactions(statements.getFilteredTransactions(args.get(0), type, minimum, maximum, from, to));
                });
        action("export-statement <account> [<from-date> <to-date>]", Access.CUSTOMER, Set.of(1, 3), args -> {
            var path = args.size() == 1 ? statements.exportStatement(args.get(0), null, null)
                    : statements.exportStatement(args.get(0), date(args.get(1)), date(args.get(2)));
            output.println("Statement exported to " + path);
        });

        // Administrator commands.
        add("create-customer <email> \"<full name>\"", Access.ADMIN, Set.of(2), (args, readPin) -> {
            String temporaryPin = readPin.get();
            if (temporaryPin == null) return false;
            Customer customer = admin.createCustomer(args.get(1), args.get(0), temporaryPin);
            output.println("Customer created: " + customer.getFullname() + " | ID: " + customer.getUserId());
            output.println("Give the customer the temporary PIN and ask them to change it after signing in.");
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
        action("unlock <user-id-or-email>", Access.ADMIN, Set.of(1), args -> {
            admin.unlockUser(args.get(0));
            output.println("User unlocked. They can try signing in again.");
        });
        add("set-pin <user-id-or-email>", Access.ADMIN, Set.of(1), (args, readPin) -> {
            String pin = readPin.get();
            if (pin == null) return false;
            admin.resetPin(args.get(0), pin);
            output.println("PIN reset. The user must change it after signing in.");
            return true;
        });
        action("close-account <account>", Access.ADMIN, Set.of(1), args -> {
            admin.closeAccount(args.get(0));
            output.println("Account closed.");
        });
        action("ledger", Access.ADMIN, Set.of(0), args -> printLedger(admin.getSystemLedger()));
        action("reverse-transaction <account> <transaction-id>", Access.ADMIN, Set.of(2), args -> {
            List<Transaction> reversed = admin.reverseTransaction(args.get(0), args.get(1));
            output.println("Reversal committed. " + reversed.size() + " compensating ledger entr" + (reversed.size() == 1 ? "y" : "ies") + " created.");
            printTransactions(reversed);
        });
        action("post-monthly-interest", Access.ADMIN, Set.of(0), args -> {
            int count = admin.postMonthlyInterest();
            output.println("Monthly interest posted to " + count + " savings account(s). Re-running this command in the same month will not duplicate interest.");
        });
        add("exit", Access.ANYONE, Set.of(0), (args, readPin) -> false);
    }

    public Command parse(String line) {
        List<String> words = split(line == null ? "" : line.strip());
        if (words.isEmpty()) return readPin -> true;
        String name = words.get(0).toLowerCase(Locale.ROOT);
        Definition definition = commands.get(name);
        if (definition == null) throw new IllegalArgumentException("Unknown command. Type help for available commands.");
        List<String> args = List.copyOf(words.subList(1, words.size()));
        if (!definition.argumentCounts().contains(args.size())) {
            throw new IllegalArgumentException("Usage: " + definition.usage());
        }
        return readPin -> {
            if (!isAvailable(definition.access())) throw new IllegalStateException(accessMessage(definition.access()));
            if (authentication.mustChangePin() && !PIN_CHANGE_ALLOWED.contains(name)) {
                throw new IllegalStateException("Change your PIN first. Type change-pin.");
            }
            return definition.operation().execute(args, readPin);
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
        add(usage, access, counts, (args, readPin) -> { action.accept(args); return true; });
    }

    private void add(String usage, Access access, Set<Integer> counts, Operation operation) {
        String name = usage.split(" ", 2)[0];
        commands.put(name, new Definition(usage, access, counts, operation));
    }

    private void printHelp() {
        User user = authentication.getCurrentUser();
        output.println(user == null ? "Guest commands" : user.getRole() + " commands");
        for (Definition definition : commands.values()) {
            String name = definition.usage().split(" ", 2)[0];
            if (isAvailable(definition.access())
                    && (!authentication.mustChangePin() || PIN_CHANGE_ALLOWED.contains(name))) {
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
        if (accountList.isEmpty()) { output.println("No accounts found."); return; }
        for (Account account : accountList) {
            output.printf("%s  %s  %s  %s%n", account.getAccountNumber(),
                    account.getClass().getSimpleName(), account.getBalance().toPlainString(),
                    account.isClosed() ? "CLOSED" : "ACTIVE");
        }
    }

    private void printCustomers(List<Customer> customerList) {
        if (customerList.isEmpty()) { output.println("No customers registered."); return; }
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

    private void printLedger(List<Transaction> ledger) { printTransactions(ledger); }

    private void printTransactions(List<Transaction> rows) {
        if (rows.isEmpty()) { output.println("No matching transactions."); return; }
        output.printf("%-19s %-18s %12s %12s  %-38s %s%n",
                "Date", "Type", "Amount", "Balance after", "Account", "Description / Reference");
        for (Transaction transaction : rows) {
            output.printf("%-19s %-18s %12s %12s  %-38s %s / %s%n",
                    transaction.getTransactionDate(), transaction.getTransactionType(),
                    transaction.getAmount().toPlainString(), transaction.getBalanceAfter().toPlainString(),
                    transaction.getAccountNumber(), transaction.getDescription(), transaction.getReferenceId());
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
        if (end != line.length()) throw new IllegalArgumentException("Use matching double quotes around arguments with spaces.");
        return words;
    }

    private static BigDecimal money(String text) {
        if (!DECIMAL.matcher(text).matches()) {
            throw new IllegalArgumentException("Amounts must be decimal numbers, for example 125.50.");
        }
        return new BigDecimal(text);
    }

    private static BigDecimal optionalMoney(String text) {
        return "-".equals(text) ? null : money(text);
    }

    private static LocalDate date(String text) {
        try { return LocalDate.parse(text); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("Dates must use YYYY-MM-DD."); }
    }

    private enum Access { ANYONE, GUEST, AUTHENTICATED, CUSTOMER, ADMIN }
    private record Definition(String usage, Access access, Set<Integer> argumentCounts, Operation operation) { }
    @FunctionalInterface private interface Operation {
        boolean execute(List<String> args, Supplier<String> readPin);
    }
}
