package com.team.banking.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.team.banking.exception.StatementExportException;
import com.team.banking.model.Account;
import com.team.banking.model.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Builds, filters and exports customer account statements. */
public class StatementService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final AccountService accountService;
    private final Clock clock;

    public StatementService(AccountService accountService) {
        this(accountService, Clock.systemDefaultZone());
    }

    public StatementService(AccountService accountService, Clock clock) {
        this.accountService = accountService;
        this.clock = clock;
    }

    public List<Transaction> getTransactions(String accountNumber) {
        Account account = accountService.getOwnedAccount(accountNumber);
        return Collections.unmodifiableList(new ArrayList<>(account.getTransactions()));
    }

    public List<Transaction> getTransactionsBetween(String accountNumber, LocalDate from, LocalDate to) {
        validateDates(from, to);
        return getFilteredTransactions(accountNumber, null, null, null, from, to);
    }

    /** Filters can be combined; null type/amount/date values mean that filter is not applied. */
    public List<Transaction> getFilteredTransactions(String accountNumber, String type,
                                                       BigDecimal minimumAmount, BigDecimal maximumAmount,
                                                       LocalDate from, LocalDate to) {
        if (minimumAmount != null && minimumAmount.signum() < 0) {
            throw new IllegalArgumentException("Minimum amount cannot be negative.");
        }
        if (maximumAmount != null && maximumAmount.signum() < 0) {
            throw new IllegalArgumentException("Maximum amount cannot be negative.");
        }
        if (minimumAmount != null && maximumAmount != null
                && minimumAmount.compareTo(maximumAmount) > 0) {
            throw new IllegalArgumentException("Minimum amount cannot exceed maximum amount.");
        }
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("Supply both date filters or neither.");
        }
        if (from != null) validateDates(from, to);

        Account account = accountService.getOwnedAccount(accountNumber);
        String wantedType = type == null || type.isBlank() || "-".equals(type)
                || "ALL".equalsIgnoreCase(type) ? null : type.trim().toUpperCase(Locale.ROOT);
        return account.getTransactions().stream()
                .filter(t -> wantedType == null || wantedType.equals(t.getTransactionType()))
                .filter(t -> minimumAmount == null || t.getAmount().compareTo(minimumAmount) >= 0)
                .filter(t -> maximumAmount == null || t.getAmount().compareTo(maximumAmount) <= 0)
                .filter(t -> from == null || (!t.getTransactionDate().toLocalDate().isBefore(from)
                        && !t.getTransactionDate().toLocalDate().isAfter(to)))
                .toList();
    }

    public String generateStatement(String accountNumber) {
        Account account = accountService.getOwnedAccount(accountNumber);
        return build(account, account.getTransactions(), "All transactions");
    }

    public String generateStatement(String accountNumber, LocalDate from, LocalDate to) {
        Account account = accountService.getOwnedAccount(accountNumber);
        List<Transaction> selected = getTransactionsBetween(accountNumber, from, to);
        return build(account, selected, "Period: " + from + " to " + to);
    }

    /** Exports the selected statement to a separate JSON file under statements/. */
    public Path exportStatement(String accountNumber, LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("Supply both date filters or neither.");
        }
        if (from != null) validateDates(from, to);
        Account account = accountService.getOwnedAccount(accountNumber);
        List<Transaction> selected = from == null
                ? account.getTransactions()
                : getTransactionsBetween(accountNumber, from, to);
        LocalDateTime generatedAt = LocalDateTime.now(clock);

        JsonObject document = new JsonObject();
        document.addProperty("accountNumber", account.getAccountNumber());
        document.addProperty("accountType", account.getClass().getSimpleName());
        document.addProperty("ownerName", account.getOwner().getFullname());
        document.addProperty("status", account.isClosed() ? "CLOSED" : "ACTIVE");
        document.addProperty("period", from == null ? "ALL" : from + " to " + to);
        document.addProperty("generatedAt", generatedAt.toString());
        document.addProperty("currentBalance", account.getBalance().toPlainString());

        JsonArray entries = new JsonArray();
        for (Transaction transaction : selected) {
            JsonObject row = new JsonObject();
            row.addProperty("transactionId", transaction.getTransactionId());
            row.addProperty("type", transaction.getTransactionType());
            row.addProperty("amount", transaction.getAmount().toPlainString());
            row.addProperty("timestamp", transaction.getTransactionDate().toString());
            row.addProperty("balanceAfter", transaction.getBalanceAfter().toPlainString());
            row.addProperty("description", transaction.getDescription());
            row.addProperty("referenceId", transaction.getReferenceId());
            entries.add(row);
        }
        document.add("transactions", entries);

        Path directory = Path.of("statements");
        String filename = account.getAccountNumber() + "-" + generatedAt.format(FILE_FORMAT) + ".json";
        Path destination = directory.resolve(filename).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, account.getAccountNumber() + ".", ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException ex) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException ex) {
                Files.deleteIfExists(temporary);
                throw ex;
            }
            return destination;
        } catch (IOException ex) {
            throw new StatementExportException("Could not export the account statement: " + ex.getMessage(), ex);
        }
    }

    private String build(Account account, List<Transaction> transactions, String periodLabel) {
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        StringBuilder lines = new StringBuilder();
        for (Transaction t : transactions) {
            lines.append(String.format("%-17s %-16s %12s%n",
                    t.getTransactionDate().format(DATE_FORMAT), t.getTransactionType(), money(t.getAmount())));
            if (isCredit(t)) totalIn = totalIn.add(t.getAmount());
            else totalOut = totalOut.add(t.getAmount());
        }
        String nl = System.lineSeparator();
        StringBuilder out = new StringBuilder();
        out.append("==================== ACCOUNT STATEMENT ====================").append(nl);
        out.append("Account : ").append(account.getAccountNumber()).append(nl);
        out.append("Type    : ").append(account.getClass().getSimpleName()).append(nl);
        out.append("Owner   : ").append(account.getOwner().getFullname()).append(nl);
        out.append("Status  : ").append(account.isClosed() ? "CLOSED" : "ACTIVE").append(nl);
        out.append(periodLabel).append(nl);
        out.append("-----------------------------------------------------------").append(nl);
        out.append(String.format("%-17s %-16s %12s%n", "Date", "Type", "Amount"));
        out.append(lines.length() == 0 ? "(no transactions)" + nl : lines);
        out.append("-----------------------------------------------------------").append(nl);
        out.append(String.format("Total in : %s%n", money(totalIn)));
        out.append(String.format("Total out: %s%n", money(totalOut)));
        out.append(String.format("Current balance: %s%n", money(account.getBalance())));
        return out.toString();
    }

    private boolean isCredit(Transaction transaction) {
        String type = transaction.getTransactionType();
        if (TransactionService.TYPE_DEPOSIT.equals(type)
                || TransactionService.TYPE_OPENING_DEPOSIT.equals(type)
                || TransactionService.TYPE_TRANSFER_IN.equals(type)
                || TransactionService.TYPE_INTEREST.equals(type)) return true;
        if (TransactionService.TYPE_REVERSAL.equals(type)) {
            String description = transaction.getDescription();
            return description.endsWith("(WITHDRAWAL)") || description.endsWith("(TRANSFER_OUT)")
                    || description.endsWith("(FEE)");
        }
        return false;
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("Both dates are required.");
        if (from.isAfter(to)) throw new IllegalArgumentException("Start date cannot be after end date.");
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
