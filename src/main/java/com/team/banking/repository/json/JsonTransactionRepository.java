package com.team.banking.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.team.banking.model.Transaction;
import com.team.banking.repository.TransactionRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One JSON file for the system-wide ledger. Entries are immutable and IDs must be unique. */
public final class JsonTransactionRepository implements TransactionRepository {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final JsonFile file;
    private final List<Transaction> transactions = new ArrayList<>();

    public JsonTransactionRepository(Path path) {
        file = new JsonFile(path);
        Set<String> ids = new HashSet<>();
        for (JsonElement element : file.read()) {
            JsonObject row = element.getAsJsonObject();
            String accountNumber = file.required(row, "accountNumber");
            String id = file.required(row, "transactionId");
            if (!ids.add(id)) throw file.malformed("Duplicate transaction ID.");
            LocalDateTime timestamp;
            try {
                timestamp = LocalDateTime.parse(file.required(row, "timestamp"), TIMESTAMP);
            } catch (DateTimeParseException ex) {
                throw file.malformed("A transaction timestamp must be an ISO-8601 local date and time.");
            }
            BigDecimal amount = file.money(row, "amount");
            // These fallback values allow a ledger created by the earlier project version to load.
            BigDecimal balanceAfter = row.has("balanceAfter")
                    ? file.money(row, "balanceAfter") : BigDecimal.ZERO;
            String description = optional(row, "description", "Imported legacy transaction");
            String referenceId = optional(row, "referenceId", "");
            transactions.add(new Transaction(id, file.required(row, "type"), amount,
                    timestamp, accountNumber, balanceAfter, description, referenceId));
        }
    }

    private String optional(JsonObject row, String field, String fallback) {
        return row.has(field) && !row.get(field).isJsonNull()
                ? row.get(field).getAsString() : fallback;
    }

    @Override
    public synchronized List<Transaction> findAll() {
        return List.copyOf(transactions);
    }

    @Override
    public synchronized List<Transaction> findByAccountNumber(String accountNumber) {
        return transactions.stream()
                .filter(t -> t.getAccountNumber().equals(accountNumber))
                .toList();
    }

    @Override
    public synchronized void saveAll(List<Transaction> updated) {
        if (updated == null) throw new IllegalArgumentException("Ledger entries are required.");
        Set<String> ids = new HashSet<>();
        JsonArray rows = new JsonArray();
        for (Transaction transaction : updated) {
            if (transaction == null || !ids.add(transaction.getTransactionId())) {
                throw new IllegalArgumentException("Ledger transactions must be non-null and have unique IDs.");
            }
            JsonObject row = new JsonObject();
            row.addProperty("accountNumber", transaction.getAccountNumber());
            row.addProperty("transactionId", transaction.getTransactionId());
            row.addProperty("type", transaction.getTransactionType());
            row.addProperty("amount", transaction.getAmount().toPlainString());
            row.addProperty("timestamp", TIMESTAMP.format(transaction.getTransactionDate()));
            row.addProperty("balanceAfter", transaction.getBalanceAfter().toPlainString());
            row.addProperty("description", transaction.getDescription());
            row.addProperty("referenceId", transaction.getReferenceId());
            rows.add(row);
        }
        file.write(rows);
        transactions.clear();
        transactions.addAll(updated);
    }
}
