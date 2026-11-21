package com.team.banking.command;

import com.team.banking.model.Account;
import com.team.banking.model.Transaction;
import com.team.banking.service.AccountService;
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

public final class CommandParser {
    private static final Pattern WORD = Pattern.compile("\\G\\s*(?:\"([^\"]*)\"|([^\\s\"]+))(?=\\s|$)");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    private final Map<String, Definition> commands = new LinkedHashMap<>();
    private final AuthenticationService authentication;
    private final PrintStream output;

    public CommandParser(AuthenticationService authentication, CustomerService customers,
                         AccountService accounts, TransactionService transactions,
                         TransferService transfers, StatementService statements, PrintStream output) {
        this.authentication = authentication;
        this.output = output;

        action("help", Access.ANYONE, Set.of(0), args -> printHelp());
        add("register <email> \"<full name>\"", Access.GUEST, Set.of(2), (args, readPassword) -> {
            String password = readPassword.get();
            if (password == null) {
                return false;
            }
            customers.registerCustomer(args.get(1), args.get(0), password);
            output.println("Customer registered. Use login to sign in.");
            return true;
        });
        add("login <email>", Access.GUEST, Set.of(1), (args, readPassword) -> {
            String password = readPassword.get();
            if (password == null) {
                return false;
            }
            var customer = authentication.login(args.get(0), password);
            output.println("Signed in as " + customer.getFullname() + ".");
            return true;
        });
        action("logout", Access.CUSTOMER, Set.of(0), args -> {
            authentication.logout();
            output.println("Signed out.");
        });
        action("open-savings <initial-deposit> <daily-limit> <minimum-balance>", Access.CUSTOMER, Set.of(3),
                args -> printNewAccount(accounts.openSavingsAccount(money(args.get(0)), money(args.get(1)), money(args.get(2)))));
        action("open-current <initial-deposit> <daily-limit> <overdraft-limit>", Access.CUSTOMER, Set.of(3),
                args -> printNewAccount(accounts.openCurrentAccount(money(args.get(0)), money(args.get(1)), money(args.get(2)))));
        action("accounts", Access.CUSTOMER, Set.of(0), args -> printAccounts(accounts.getMyAccounts()));
        action("balance <account>", Access.CUSTOMER, Set.of(1),
                args -> output.println("Balance " + accounts.getBalance(args.get(0)).toPlainString()));
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
        add("exit", Access.ANYONE, Set.of(0), (args, readPassword) -> false);
    }

    public Command parse(String line) {
        List<String> words = split(line.strip());
        if (words.isEmpty()) {
            return readPassword -> true;
        }
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
                throw new IllegalStateException(definition.access() == Access.CUSTOMER
                        ? "You must log in first." : "Sign out before using this command.");
            }
            return definition.operation().execute(args, readPassword);
        };
    }

    private void action(String usage, Access access, Set<Integer> argumentCounts, Consumer<List<String>> action) {
        add(usage, access, argumentCounts, (args, readPassword) -> {
            action.accept(args);
            return true;
        });
    }

    private void add(String usage, Access access, Set<Integer> argumentCounts, Operation operation) {
        String name = usage.split(" ", 2)[0];
        commands.put(name, new Definition(usage, access, argumentCounts, operation));
    }

    private void printHelp() {
        output.println(authentication.isLoggedIn() ? "Customer commands" : "Guest commands");
        for (Definition definition : commands.values()) {
            if (isAvailable(definition.access())) {
                output.println("  " + definition.usage());
            }
        }
    }

    private boolean isAvailable(Access access) {
        return switch (access) {
            case ANYONE -> true;
            case GUEST -> !authentication.isLoggedIn();
            case CUSTOMER -> authentication.isLoggedIn();
        };
    }

    private void printNewAccount(Account account) {
        output.println("Opened " + account.getClass().getSimpleName() + " " + account.getAccountNumber());
    }

    private void printAccounts(List<Account> accounts) {
        if (accounts.isEmpty()) {
            output.println("You have no accounts.");
        }
        for (Account account : accounts) {
            output.printf("%s  %s  %s%n", account.getAccountNumber(), account.getClass().getSimpleName(),
                    account.getBalance().toPlainString());
        }
    }

    private void printTransaction(String action, Transaction transaction) {
        output.printf("%s %s (transaction %s)%n", action, transaction.getAmount().toPlainString(),
                transaction.getTransactionId());
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
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Dates must use YYYY-MM-DD.");
        }
    }

    private enum Access { ANYONE, GUEST, CUSTOMER }

    private record Definition(String usage, Access access, Set<Integer> argumentCounts, Operation operation) {}

    @FunctionalInterface
    private interface Operation {
        boolean execute(List<String> args, Supplier<String> readPassword);
    }
}
