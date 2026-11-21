package com.team.banking.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.team.banking.model.Transaction;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JsonTransactions {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final JsonFile file;

    JsonTransactions(Path path) {
        file = new JsonFile(path);
    }

    Map<String, List<Transaction>> read() {
        Map<String, List<Transaction>> histories = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : file.read()) {
            JsonObject row = element.getAsJsonObject();
            String accountNumber = file.required(row, "accountNumber");
            String id = file.required(row, "transactionId");
            if (!ids.add(id)) {
                throw file.malformed("Duplicate transaction ID.");
            }
            LocalDateTime timestamp;
            try {
                timestamp = LocalDateTime.parse(file.required(row, "timestamp"), TIMESTAMP);
            } catch (DateTimeParseException e) {
                throw file.malformed("A transaction timestamp must be an ISO-8601 local date and time.");
            }
            Transaction transaction = new Transaction(id, file.required(row, "type"),
                    file.money(row, "amount"), timestamp);
            histories.computeIfAbsent(accountNumber, ignored -> new ArrayList<>()).add(transaction);
        }
        return histories;
    }

    void write(Map<String, List<Transaction>> histories) {
        JsonArray rows = new JsonArray();
        Set<String> ids = new HashSet<>();
        for (var entry : histories.entrySet()) {
            for (Transaction transaction : entry.getValue()) {
                if (!ids.add(transaction.getTransactionId())) {
                    throw new IllegalArgumentException("Transaction IDs must be unique.");
                }
                JsonObject row = new JsonObject();
                row.addProperty("accountNumber", entry.getKey());
                row.addProperty("transactionId", transaction.getTransactionId());
                row.addProperty("type", transaction.getTransactionType());
                row.addProperty("amount", transaction.getAmount().toPlainString());
                row.addProperty("timestamp", TIMESTAMP.format(transaction.getTransactionDate()));
                rows.add(row);
            }
        }
        file.write(rows);
    }
}
