package com.team.banking.service;

import com.team.banking.model.Account;
import com.team.banking.model.Transaction;
import com.team.banking.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionService {

    public static final String TYPE_OPENING_DEPOSIT = "OPENING_DEPOSIT";
    public static final String TYPE_DEPOSIT = "DEPOSIT";
    public static final String TYPE_WITHDRAWAL = "WITHDRAWAL";
    public static final String TYPE_TRANSFER_IN = "TRANSFER_IN";
    public static final String TYPE_TRANSFER_OUT = "TRANSFER_OUT";

    private final AccountRepository accountRepository;
    private final AccountService accountService;

    public TransactionService(AccountRepository accountRepository, AccountService accountService) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
    }

    public Transaction deposit(String accountNumber, BigDecimal amount) {
        Validators.requirePositiveMoney(amount, "Deposit amount");
        Account account = accountService.getOwnedAccount(accountNumber);

        account.deposit(amount);
        return record(account, TYPE_DEPOSIT, amount);
    }

    public Transaction withdraw(String accountNumber, BigDecimal amount) {
        Validators.requirePositiveMoney(amount, "Withdrawal amount");
        Account account = accountService.getOwnedAccount(accountNumber);

        checkDailyLimit(account, amount);
        account.withdraw(amount); // the model enforces minimum balance / overdraft
        return record(account, TYPE_WITHDRAWAL, amount);
    }

    /** Throws if this amount would push today's outgoing money over the daily limit. */
    public void checkDailyLimit(Account account, BigDecimal amount) {
        BigDecimal alreadyOut = outgoingToday(account);
        BigDecimal limit = account.getDailyWithdrawalLimit();

        if (alreadyOut.add(amount).compareTo(limit) > 0) {
            BigDecimal remaining = limit.subtract(alreadyOut).max(BigDecimal.ZERO);
            throw new IllegalArgumentException(
                    "Daily withdrawal limit exceeded. You can still take out "
                            + remaining.toPlainString() + " today.");
        }
    }

    /** Creates a Transaction, attaches it to the account and saves the account. */
    public Transaction record(Account account, String type, BigDecimal amount) {
        Transaction transaction = new Transaction(
                UUID.randomUUID().toString(), type, amount, LocalDateTime.now());
        account.getTransactions().add(transaction);
        accountRepository.save(account);
        return transaction;
    }

    private BigDecimal outgoingToday(Account account) {
        LocalDate today = LocalDate.now();
        BigDecimal total = BigDecimal.ZERO;

        for (Transaction t : account.getTransactions()) {
            boolean isOutgoing = TYPE_WITHDRAWAL.equals(t.getTransactionType())
                    || TYPE_TRANSFER_OUT.equals(t.getTransactionType());
            boolean isToday = t.getTransactionDate().toLocalDate().equals(today);

            if (isOutgoing && isToday) {
                total = total.add(t.getAmount());
            }
        }
        return total;
    }
}
