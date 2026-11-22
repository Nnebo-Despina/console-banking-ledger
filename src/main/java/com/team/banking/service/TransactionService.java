package com.team.banking.service;

import com.team.banking.model.Account;
import com.team.banking.model.Transaction;
import com.team.banking.model.UserRole;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coordinates account mutations with the separately persisted append-only transaction ledger. */
public class TransactionService {
    public static final String TYPE_OPENING_DEPOSIT = "OPENING_DEPOSIT";
    public static final String TYPE_DEPOSIT = "DEPOSIT";
    public static final String TYPE_WITHDRAWAL = "WITHDRAWAL";
    public static final String TYPE_TRANSFER_IN = "TRANSFER_IN";
    public static final String TYPE_TRANSFER_OUT = "TRANSFER_OUT";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuthenticationService authenticationService;
    private final Clock clock;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              AuthenticationService authenticationService,
                              Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.authenticationService = authenticationService;
        this.clock = clock;
    }

    public synchronized Transaction deposit(String accountNumber, BigDecimal amount) {
        Validators.requirePositiveMoney(amount, "Deposit amount");
        Account account = requireOwnedCustomerAccount(accountNumber);
        BigDecimal previousBalance = account.getBalance();
        account.deposit(amount);
        try {
            return commit(List.of(account), List.of(new Pending(account, TYPE_DEPOSIT, amount,
                    "Cash deposit", ""))).get(0);
        } catch (RuntimeException ex) {
            account.restoreBalanceAfterFailedCommit(previousBalance);
            throw ex;
        }
    }

    public synchronized Transaction withdraw(String accountNumber, BigDecimal amount) {
        Validators.requirePositiveMoney(amount, "Withdrawal amount");
        Account account = requireOwnedCustomerAccount(accountNumber);
        checkDailyLimit(account, amount);
        BigDecimal previousBalance = account.getBalance();
        account.withdraw(amount);
        try {
            return commit(List.of(account), List.of(new Pending(account, TYPE_WITHDRAWAL, amount,
                    "Cash withdrawal", ""))).get(0);
        } catch (RuntimeException ex) {
            account.restoreBalanceAfterFailedCommit(previousBalance);
            throw ex;
        }
    }

    /** Persist a zero-opening-balance account under the same lock used by financial operations. */
    synchronized void persistNewAccount(Account account) {
        accountRepository.save(account);
    }

    /** Close and persist an account while excluding concurrent deposits, withdrawals and transfers. */
    synchronized void closeAccount(Account account) {
        account.close();
        try {
            accountRepository.save(account);
        } catch (RuntimeException ex) {
            account.restoreClosedState(false);
            throw ex;
        }
    }

    /** Called by AccountService after an account has been constructed with its opening balance. */
    synchronized Transaction recordOpeningDeposit(Account account, BigDecimal amount) {
        Validators.requirePositiveMoney(amount, "Opening deposit");
        return commit(List.of(account), List.of(new Pending(account, TYPE_OPENING_DEPOSIT, amount,
                "Initial opening deposit", ""))).get(0);
    }

    /** Transfers are serialized and committed as one account-file replacement. */
    public synchronized Transaction transfer(Account source, Account destination, BigDecimal amount) {
        var currentUser = authenticationService.requireRole(com.team.banking.model.UserRole.CUSTOMER);
        if (source == null || !source.getOwner().getUserId().equals(currentUser.getUserId())) {
            throw new IllegalArgumentException("You do not have access to the source account.");
        }
        Validators.requirePositiveMoney(amount, "Transfer amount");
        if (source == null || destination == null) throw new IllegalArgumentException("Both accounts are required.");
        if (source.getAccountNumber().equals(destination.getAccountNumber())) {
            throw new IllegalArgumentException("You cannot transfer to the same account.");
        }
        checkDailyLimit(source, amount);
        BigDecimal sourceBefore = source.getBalance();
        BigDecimal destinationBefore = destination.getBalance();
        source.withdraw(amount);
        try {
            destination.deposit(amount);
        } catch (RuntimeException ex) {
            source.restoreBalanceAfterFailedCommit(sourceBefore);
            throw ex;
        }

        String transferReference = UUID.randomUUID().toString();
        try {
            List<Transaction> entries = commit(List.of(source, destination), List.of(
                    new Pending(source, TYPE_TRANSFER_OUT, amount, "Transfer to " + destination.getAccountNumber(), transferReference),
                    new Pending(destination, TYPE_TRANSFER_IN, amount, "Transfer from " + source.getAccountNumber(), transferReference)));
            return entries.get(0);
        } catch (RuntimeException ex) {
            source.restoreBalanceAfterFailedCommit(sourceBefore);
            destination.restoreBalanceAfterFailedCommit(destinationBefore);
            throw ex;
        }
    }

    public synchronized void checkDailyLimit(Account account, BigDecimal amount) {
        if (account == null || amount == null) throw new IllegalArgumentException("Account and amount are required.");
        BigDecimal alreadyOut = outgoingToday(account);
        BigDecimal limit = account.getDailyWithdrawalLimit();
        if (alreadyOut.add(amount).compareTo(limit) > 0) {
            BigDecimal remaining = limit.subtract(alreadyOut).max(BigDecimal.ZERO);
            throw new IllegalArgumentException("Daily withdrawal limit exceeded. You can still take out "
                    + remaining.toPlainString() + " today.");
        }
    }

    private Account requireOwnedCustomerAccount(String accountNumber) {
        var user = authenticationService.requireRole(UserRole.CUSTOMER);
        String number = Validators.requireText(accountNumber, "Account number");
        Account account = accountRepository.findByAccountNumber(number)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (!account.getOwner().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("You do not have access to this account.");
        }
        return account;
    }

    private BigDecimal outgoingToday(Account account) {
        LocalDate today = LocalDate.now(clock);
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : account.getTransactions()) {
            boolean outgoing = TYPE_WITHDRAWAL.equals(transaction.getTransactionType())
                    || TYPE_TRANSFER_OUT.equals(transaction.getTransactionType());
            if (outgoing && transaction.getTransactionDate().toLocalDate().equals(today)) {
                total = total.add(transaction.getAmount());
            }
        }
        return total;
    }

    /**
     * Ledger file is staged first; the account file's transactionCount is the commit marker.
     * On startup, JsonAccountRepository discards any ledger tail not committed by that marker.
     */
    private List<Transaction> commit(List<Account> accounts, List<Pending> pending) {
        List<Transaction> previousLedger = transactionRepository.findAll();
        Map<Account, Integer> oldTransactionSizes = new HashMap<>();
        for (Account account : accounts) oldTransactionSizes.put(account, account.getTransactions().size());

        List<Transaction> additions = new ArrayList<>();
        for (Pending item : pending) {
            Transaction transaction = new Transaction(UUID.randomUUID().toString(), item.type(), item.amount(),
                    LocalDateTime.now(clock), item.account().getAccountNumber(), item.account().getBalance(),
                    item.description(), item.referenceId());
            item.account().getTransactions().add(transaction);
            additions.add(transaction);
        }
        List<Transaction> updatedLedger = new ArrayList<>(previousLedger);
        updatedLedger.addAll(additions);
        try {
            transactionRepository.saveAll(updatedLedger);
            accountRepository.saveAll(accounts);
            return additions;
        } catch (RuntimeException ex) {
            for (Map.Entry<Account, Integer> entry : oldTransactionSizes.entrySet()) {
                List<Transaction> history = entry.getKey().getTransactions();
                while (history.size() > entry.getValue()) history.remove(history.size() - 1);
            }
            try {
                transactionRepository.saveAll(previousLedger);
            } catch (RuntimeException rollbackError) {
                ex.addSuppressed(rollbackError);
            }
            throw ex;
        }
    }

    private record Pending(Account account, String type, BigDecimal amount,
                           String description, String referenceId) { }
}
