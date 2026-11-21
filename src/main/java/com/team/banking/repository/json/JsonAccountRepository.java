package com.team.banking.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.team.banking.model.Account;
import com.team.banking.model.CurrentAccount;
import com.team.banking.model.Customer;
import com.team.banking.model.SavingsAccount;
import com.team.banking.model.Transaction;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.CustomerRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JsonAccountRepository implements AccountRepository {
    private final JsonFile accountsFile;
    private final JsonTransactions transactionsFile;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, JsonObject> savedAccounts = new LinkedHashMap<>();
    private final Map<String, List<Transaction>> savedHistories = new LinkedHashMap<>();

    public JsonAccountRepository(Path accountsPath, Path transactionsPath, CustomerRepository customers) {
        accountsFile = new JsonFile(accountsPath);
        transactionsFile = new JsonTransactions(transactionsPath);
        Map<String, List<Transaction>> histories = transactionsFile.read();
        for (JsonElement element : accountsFile.read()) {
            JsonObject row = element.getAsJsonObject();
            String number = accountsFile.required(row, "accountNumber");
            if (accounts.containsKey(number)) {
                throw accountsFile.malformed("Duplicate account number.");
            }
            String ownerId = accountsFile.required(row, "ownerId");
            Customer owner = customers.findById(ownerId)
                    .orElseThrow(() -> accountsFile.malformed("An account refers to an unknown owner."));
            Account account = restore(row, number, owner);
            List<Transaction> history = histories.getOrDefault(number, List.of());
            int count = accountsFile.count(row, "transactionCount");
            if (count > history.size()) {
                throw accountsFile.malformed("An account's transaction history is incomplete. Check transactions.json.");
            }
            List<Transaction> committed = List.copyOf(history.subList(0, count));
            account.getTransactions().addAll(committed);
            accounts.put(number, account);
            savedAccounts.put(number, row);
            savedHistories.put(number, committed);
        }
        for (Account account : accounts.values()) {
            account.getOwner().addAccount(account);
        }
    }

    private Account restore(JsonObject row, String number, Customer owner) {
        BigDecimal balance = accountsFile.money(row, "balance");
        BigDecimal dailyLimit = accountsFile.money(row, "dailyWithdrawalLimit");
        // AccountFactory generates a new number. The constructors accept the saved one.
        return switch (accountsFile.required(row, "type")) {
            case "SAVINGS" -> new SavingsAccount(number, owner, balance, dailyLimit,
                    accountsFile.money(row, "minimumBalance"));
            case "CURRENT" -> new CurrentAccount(number, owner, balance, dailyLimit,
                    accountsFile.money(row, "overdraftLimit"));
            default -> throw accountsFile.malformed("Unknown account type. Expected SAVINGS or CURRENT.");
        };
    }

    @Override
    public synchronized void save(Account account) {
        String number = account.getAccountNumber();
        List<Transaction> history = List.copyOf(account.getTransactions());
        List<Transaction> previous = savedHistories.getOrDefault(number, List.of());
        if (history.size() < previous.size() || !history.subList(0, previous.size()).equals(previous)) {
            throw new IllegalArgumentException("Saved transaction history cannot be changed.");
        }

        Map<String, List<Transaction>> updatedHistories = new LinkedHashMap<>(savedHistories);
        updatedHistories.put(number, history);
        Map<String, JsonObject> updatedAccounts = new LinkedHashMap<>(savedAccounts);
        updatedAccounts.put(number, describe(account, history.size()));
        JsonArray rows = new JsonArray();
        updatedAccounts.values().forEach(rows::add);

        // The account's count commits the new transactions. An interrupted write leaves the old prefix readable.
        transactionsFile.write(updatedHistories);
        accountsFile.write(rows);
        savedHistories.put(number, history);
        savedAccounts.put(number, updatedAccounts.get(number));
        accounts.put(number, account);
    }

    private JsonObject describe(Account account, int transactionCount) {
        JsonObject row = new JsonObject();
        row.addProperty("accountNumber", account.getAccountNumber());
        row.addProperty("ownerId", account.getOwner().getUserId());
        row.addProperty("balance", account.getBalance().toPlainString());
        row.addProperty("dailyWithdrawalLimit", account.getDailyWithdrawalLimit().toPlainString());
        row.addProperty("transactionCount", Integer.toString(transactionCount));
        if (account instanceof SavingsAccount savings) {
            row.addProperty("type", "SAVINGS");
            row.addProperty("minimumBalance", savings.getMinimumBalance().toPlainString());
        } else if (account instanceof CurrentAccount current) {
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
        List<Account> matches = new ArrayList<>();
        for (Account account : accounts.values()) {
            if (account.getOwner().getUserId().equals(ownerId)) {
                matches.add(account);
            }
        }
        return matches;
    }
}
