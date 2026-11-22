package com.team.banking.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.team.banking.model.Account;
import com.team.banking.model.AccountFactory;
import com.team.banking.model.Customer;
import com.team.banking.model.Transaction;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.TransactionRepository;
import com.team.banking.repository.UserRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persists account snapshots. transactionCount is the commit marker for the separate ledger file. */
public final class JsonAccountRepository implements AccountRepository {
    private final JsonFile file;
    private final TransactionRepository transactionRepository;
    private final Map<String, Account> accounts = new LinkedHashMap<>();

    public JsonAccountRepository(Path accountsPath, UserRepository users,
                                 TransactionRepository transactionRepository) {
        this.file = new JsonFile(accountsPath);
        this.transactionRepository = transactionRepository;
        List<Transaction> rawLedger = transactionRepository.findAll();
        Map<String, List<Transaction>> histories = new LinkedHashMap<>();
        for (Transaction transaction : rawLedger) {
            histories.computeIfAbsent(transaction.getAccountNumber(), ignored -> new ArrayList<>())
                    .add(transaction);
        }

        Map<String, Integer> committedCounts = new LinkedHashMap<>();
        for (JsonElement element : file.read()) {
            JsonObject row = element.getAsJsonObject();
            String number = file.required(row, "accountNumber");
            if (accounts.containsKey(number)) throw file.malformed("Duplicate account number.");
            String ownerId = file.required(row, "ownerId");
            Customer owner = users.findById(ownerId)
                    .filter(Customer.class::isInstance)
                    .map(Customer.class::cast)
                    .orElseThrow(() -> file.malformed("An account refers to an unknown customer owner."));
            Account account = restore(row, number, owner);
            List<Transaction> history = histories.getOrDefault(number, List.of());
            int count = row.has("transactionCount")
                    ? file.count(row, "transactionCount") : history.size();
            if (count > history.size()) {
                throw file.malformed("An account's transaction history is incomplete. Check transactions.json.");
            }
            List<Transaction> committed = history.subList(0, count);
            account.getTransactions().addAll(committed);
            committedCounts.put(number, count);
            accounts.put(number, account);
            owner.addAccount(account);
        }

        // Recover cleanly from a crash after writing transactions.json but before accounts.json.
        // The account file's transactionCount says which ledger prefix was actually committed.
        List<Transaction> committedLedger = new ArrayList<>();
        Map<String, Integer> seenPerAccount = new LinkedHashMap<>();
        for (Transaction transaction : rawLedger) {
            String number = transaction.getAccountNumber();
            int seen = seenPerAccount.getOrDefault(number, 0);
            int committedCount = committedCounts.getOrDefault(number, 0);
            if (seen < committedCount) committedLedger.add(transaction);
            seenPerAccount.put(number, seen + 1);
        }
        if (committedLedger.size() != rawLedger.size()) {
            transactionRepository.saveAll(committedLedger);
        }
    }

    private Account restore(JsonObject row, String number, Customer owner) {
        BigDecimal balance = file.money(row, "balance");
        BigDecimal dailyLimit = file.money(row, "dailyWithdrawalLimit");
        Account restored = switch (file.required(row, "type")) {
            case "SAVINGS" -> AccountFactory.createSavingsAccount(number, owner, balance, dailyLimit,
                    file.money(row, "minimumBalance"));
            case "CURRENT" -> AccountFactory.createCurrentAccount(number, owner, balance, dailyLimit,
                    file.money(row, "overdraftLimit"));
            default -> throw file.malformed("Unknown account type. Expected SAVINGS or CURRENT.");
        };
        restored.restoreClosedState(row.has("closed") && Boolean.parseBoolean(row.get("closed").getAsString()));
        return restored;
    }

    @Override
    public synchronized void save(Account account) {
        saveAll(List.of(account));
    }

    @Override
    public synchronized void saveAll(Collection<Account> updates) {
        if (updates == null || updates.isEmpty()) return;
        Map<String, Account> merged = new LinkedHashMap<>(accounts);
        for (Account account : updates) {
            if (account == null) throw new IllegalArgumentException("Cannot save a null account.");
            merged.put(account.getAccountNumber(), account);
        }
        JsonArray rows = new JsonArray();
        for (Account account : merged.values()) {
            rows.add(describe(account));
        }
        file.write(rows); // One atomic replacement commits all affected account balances together.
        accounts.clear();
        accounts.putAll(merged);
    }

    private JsonObject describe(Account account) {
        JsonObject row = new JsonObject();
        row.addProperty("accountNumber", account.getAccountNumber());
        row.addProperty("ownerId", account.getOwner().getUserId());
        row.addProperty("balance", account.getBalance().toPlainString());
        row.addProperty("dailyWithdrawalLimit", account.getDailyWithdrawalLimit().toPlainString());
        row.addProperty("transactionCount", Integer.toString(account.getTransactions().size()));
        row.addProperty("closed", Boolean.toString(account.isClosed()));
        if (account instanceof com.team.banking.model.SavingsAccount savings) {
            row.addProperty("type", "SAVINGS");
            row.addProperty("minimumBalance", savings.getMinimumBalance().toPlainString());
        } else if (account instanceof com.team.banking.model.CurrentAccount current) {
            row.addProperty("type", "CURRENT");
            row.addProperty("overdraftLimit", current.getOverdraftLimit().toPlainString());
        } else {
            throw new IllegalArgumentException("Unsupported account type.");
        }
        return row;
    }

    @Override
    public synchronized Optional<Account> findByAccountNumber(String accountNumber) {
        return Optional.ofNullable(accounts.get(accountNumber));
    }

    @Override
    public synchronized List<Account> findByOwnerId(String ownerId) {
        return accounts.values().stream()
                .filter(account -> account.getOwner().getUserId().equals(ownerId))
                .toList();
    }

    @Override
    public synchronized List<Account> findAll() {
        return new ArrayList<>(accounts.values());
    }
}
