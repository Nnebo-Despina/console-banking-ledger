package com.team.banking.service;

import com.team.banking.model.Account;
import com.team.banking.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StatementService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final AccountService accountService;

    public StatementService(AccountService accountService) { this.accountService = accountService; }

    public List<Transaction> getTransactions(String accountNumber) {
        Account account = accountService.getOwnedAccount(accountNumber);
        return Collections.unmodifiableList(new ArrayList<>(account.getTransactions()));
    }

    public List<Transaction> getTransactionsBetween(String accountNumber, LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("Both dates are required.");
        if (from.isAfter(to)) throw new IllegalArgumentException("Start date cannot be after end date.");
        Account account = accountService.getOwnedAccount(accountNumber);
        return account.getTransactions().stream()
                .filter(t -> !t.getTransactionDate().toLocalDate().isBefore(from)
                        && !t.getTransactionDate().toLocalDate().isAfter(to))
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

    private String build(Account account, List<Transaction> transactions, String periodLabel) {
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        StringBuilder lines = new StringBuilder();
        for (Transaction t : transactions) {
            lines.append(String.format("%-17s %-16s %12s%n",
                    t.getTransactionDate().format(DATE_FORMAT), t.getTransactionType(), money(t.getAmount())));
            if (isCredit(t.getTransactionType())) totalIn = totalIn.add(t.getAmount());
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

    private boolean isCredit(String type) {
        return TransactionService.TYPE_DEPOSIT.equals(type)
                || TransactionService.TYPE_OPENING_DEPOSIT.equals(type)
                || TransactionService.TYPE_TRANSFER_IN.equals(type);
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
