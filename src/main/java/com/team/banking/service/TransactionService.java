package com.team.banking.service;

import com.team.banking.exception.AccountNotFoundException;
import com.team.banking.exception.DailyLimitExceededException;
import com.team.banking.exception.TransactionNotReversibleException;
import com.team.banking.exception.UnauthorizedActionException;
import com.team.banking.model.Account;
import com.team.banking.model.CurrentAccount;
import com.team.banking.model.SavingsAccount;
import com.team.banking.model.Transaction;
import com.team.banking.model.UserRole;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.TransactionRepository;
import com.team.banking.strategy.CurrentAccountFeeStrategy;
import com.team.banking.strategy.InterestStrategy;
import com.team.banking.strategy.SavingsInterestStrategy;
import com.team.banking.strategy.WithdrawalFeeStrategy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Coordinates concurrent account changes and the separately persisted append-only ledger. */
public class TransactionService {
    public static final String TYPE_OPENING_DEPOSIT = "OPENING_DEPOSIT";
    public static final String TYPE_DEPOSIT = "DEPOSIT";
    public static final String TYPE_WITHDRAWAL = "WITHDRAWAL";
    public static final String TYPE_TRANSFER_IN = "TRANSFER_IN";
    public static final String TYPE_TRANSFER_OUT = "TRANSFER_OUT";
    public static final String TYPE_FEE = "FEE";
    public static final String TYPE_INTEREST = "INTEREST";
    public static final String TYPE_REVERSAL = "REVERSAL";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuthenticationService authenticationService;
    private final Clock clock;
    private final WithdrawalFeeStrategy feeStrategy;
    private final InterestStrategy interestStrategy;

    /* The ledger lock serializes ledger snapshots; account locks are then acquired in sorted order. */
    private final ReentrantLock ledgerLock = new ReentrantLock(true);
    private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              AuthenticationService authenticationService,
                              Clock clock) {
        this(accountRepository, transactionRepository, authenticationService, clock,
                new CurrentAccountFeeStrategy(5, configuredWithdrawalFee()), new SavingsInterestStrategy());
    }

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              AuthenticationService authenticationService,
                              Clock clock,
                              WithdrawalFeeStrategy feeStrategy,
                              InterestStrategy interestStrategy) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.authenticationService = authenticationService;
        this.clock = clock;
        this.feeStrategy = feeStrategy;
        this.interestStrategy = interestStrategy;
    }

    private static BigDecimal configuredWithdrawalFee() {
        String configured = System.getenv().getOrDefault("BANK_CURRENT_WITHDRAWAL_FEE", "50.00");
        try {
            BigDecimal fee = new BigDecimal(configured);
            if (fee.signum() < 0 || fee.scale() > 2) throw new NumberFormatException();
            return fee;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("BANK_CURRENT_WITHDRAWAL_FEE must be a non-negative amount with at most two decimals.");
        }
    }

    public Transaction deposit(String accountNumber, BigDecimal amount) {
        Validators.requirePositiveMoney(amount, "Deposit amount");
        Account account = requireOwnedCustomerAccount(accountNumber);
        return withAccountLocks(List.of(account), () -> {
            BigDecimal previousBalance = account.getBalance();
            try {
                account.deposit(amount);
                return commit(List.of(account), List.of(pending(account, TYPE_DEPOSIT, amount,
                        "Cash deposit", ""))).get(0);
            } catch (RuntimeException ex) {
                account.restoreBalanceAfterFailedCommit(previousBalance);
                throw ex;
            }
        });
    }

    public Transaction withdraw(String accountNumber, BigDecimal amount) {
        Validators.requirePositiveMoney(amount, "Withdrawal amount");
        Account account = requireOwnedCustomerAccount(accountNumber);
        return withAccountLocks(List.of(account), () -> {
            BigDecimal previousBalance = account.getBalance();
            try {
                checkDailyLimitUnlocked(account, amount);
                YearMonth month = YearMonth.now(clock);
                int previousWithdrawals = monthlyWithdrawalCount(account, month);
                BigDecimal fee = feeStrategy.calculateFee(account, previousWithdrawals);
                if (fee == null || fee.signum() < 0) {
                    throw new IllegalStateException("The withdrawal fee strategy returned an invalid fee.");
                }

                account.withdraw(amount);
                BigDecimal balanceAfterWithdrawal = account.getBalance();
                List<Pending> entries = new ArrayList<>();
                entries.add(new Pending(account, TYPE_WITHDRAWAL, amount,
                        "Cash withdrawal", "", balanceAfterWithdrawal));
                if (fee.signum() > 0) {
                    account.withdraw(fee); // The fee must also respect savings/overdraft rules.
                    entries.add(pending(account, TYPE_FEE, fee, "Current-account fee after free monthly withdrawals", ""));
                }
                return commit(List.of(account), entries).get(0);
            } catch (RuntimeException ex) {
                account.restoreBalanceAfterFailedCommit(previousBalance);
                throw ex;
            }
        });
    }

    /** Persist an account without an opening deposit under the same coordination locks. */
    void persistNewAccount(Account account) {
        withAccountLocks(List.of(account), () -> {
            accountRepository.save(account);
            return null;
        });
    }

    /** Close and persist an account while excluding simultaneous financial operations. */
    void closeAccount(Account account) {
        withAccountLocks(List.of(account), () -> {
            account.close();
            try {
                accountRepository.save(account);
            } catch (RuntimeException ex) {
                account.restoreClosedState(false);
                throw ex;
            }
            return null;
        });
    }

    /** Called after construction of an account with its opening balance. */
    Transaction recordOpeningDeposit(Account account, BigDecimal amount) {
        Validators.requirePositiveMoney(amount, "Opening deposit");
        return withAccountLocks(List.of(account), () -> commit(List.of(account),
                List.of(pending(account, TYPE_OPENING_DEPOSIT, amount, "Initial opening deposit", ""))).get(0));
    }

    /** Transfers are committed as one logical operation. Account locks are always ordered by account number. */
    public Transaction transfer(Account source, Account destination, BigDecimal amount) {
        authenticationService.requireRole(UserRole.CUSTOMER);
        Validators.requirePositiveMoney(amount, "Transfer amount");
        if (source == null || destination == null) throw new IllegalArgumentException("Both accounts are required.");
        if (!source.getOwner().getUserId().equals(authenticationService.getCurrentUser().getUserId())) {
            throw new UnauthorizedActionException("You do not have access to the source account.");
        }
        if (source.getAccountNumber().equals(destination.getAccountNumber())) {
            throw new IllegalArgumentException("You cannot transfer to the same account.");
        }

        return withAccountLocks(List.of(source, destination), () -> {
            BigDecimal sourceBefore = source.getBalance();
            BigDecimal destinationBefore = destination.getBalance();
            try {
                checkDailyLimitUnlocked(source, amount);
                source.withdraw(amount);
                destination.deposit(amount);
                String reference = UUID.randomUUID().toString();
                List<Pending> entries = List.of(
                        pending(source, TYPE_TRANSFER_OUT, amount,
                                "Transfer to " + destination.getAccountNumber(), reference),
                        pending(destination, TYPE_TRANSFER_IN, amount,
                                "Transfer from " + source.getAccountNumber(), reference));
                return commit(List.of(source, destination), entries).get(0);
            } catch (RuntimeException ex) {
                source.restoreBalanceAfterFailedCommit(sourceBefore);
                destination.restoreBalanceAfterFailedCommit(destinationBefore);
                throw ex;
            }
        });
    }

    /** Admin-only compensating reversal. A transfer reversal compensates both ledger sides together. */
    public List<Transaction> reverse(String accountNumber, String transactionId) {
        authenticationService.requireRole(UserRole.ADMIN);
        String number = Validators.requireText(accountNumber, "Account number");
        String id = Validators.requireText(transactionId, "Transaction ID");
        Account selected = accountRepository.findByAccountNumber(number)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
        Transaction original = selected.getTransactions().stream()
                .filter(t -> t.getTransactionId().equals(id))
                .findFirst().orElseThrow(() -> new TransactionNotReversibleException("Transaction was not found on this account."));

        List<Transaction> originals = new ArrayList<>();
        originals.add(original);
        if (TYPE_TRANSFER_IN.equals(original.getTransactionType()) || TYPE_TRANSFER_OUT.equals(original.getTransactionType())) {
            String ref = original.getReferenceId();
            if (ref.isBlank()) throw new TransactionNotReversibleException("This legacy transfer has no matching reference and cannot be reversed safely.");
            String counterpartType = TYPE_TRANSFER_IN.equals(original.getTransactionType()) ? TYPE_TRANSFER_OUT : TYPE_TRANSFER_IN;
            Transaction counterpart = transactionRepository.findAll().stream()
                    .filter(t -> t.getReferenceId().equals(ref)
                            && t.getTransactionType().equals(counterpartType)
                            && !t.getTransactionId().equals(original.getTransactionId()))
                    .findFirst().orElseThrow(() -> new TransactionNotReversibleException("The matching transfer entry could not be found."));
            originals.add(counterpart);
        } else if (!Set.of(TYPE_DEPOSIT, TYPE_OPENING_DEPOSIT, TYPE_WITHDRAWAL, TYPE_FEE, TYPE_INTEREST)
                .contains(original.getTransactionType())) {
            throw new TransactionNotReversibleException("This transaction type cannot be reversed.");
        }

        List<Account> affected = new ArrayList<>();
        Map<String, Account> byNumber = new HashMap<>();
        for (Transaction item : originals) {
            Account account = accountRepository.findByAccountNumber(item.getAccountNumber())
                    .orElseThrow(() -> new AccountNotFoundException("An account involved in this transaction no longer exists."));
            affected.add(account);
            byNumber.put(account.getAccountNumber(), account);
        }

        return withAccountLocks(affected, () -> {
            List<Transaction> ledger = transactionRepository.findAll();
            for (Transaction item : originals) {
                boolean alreadyReversed = ledger.stream().anyMatch(t -> TYPE_REVERSAL.equals(t.getTransactionType())
                        && t.getReferenceId().equals(item.getTransactionId()));
                if (alreadyReversed) {
                    throw new TransactionNotReversibleException("This transaction has already been reversed.");
                }
            }

            Map<Account, BigDecimal> oldBalances = new HashMap<>();
            for (Account account : affected) oldBalances.put(account, account.getBalance());
            try {
                List<Pending> reversals = new ArrayList<>();
                for (Transaction item : originals) {
                    Account account = byNumber.get(item.getAccountNumber());
                    String type = item.getTransactionType();
                    if (TYPE_DEPOSIT.equals(type) || TYPE_OPENING_DEPOSIT.equals(type)
                            || TYPE_TRANSFER_IN.equals(type) || TYPE_INTEREST.equals(type)) {
                        account.withdraw(item.getAmount());
                    } else {
                        account.deposit(item.getAmount());
                    }
                    reversals.add(pending(account, TYPE_REVERSAL, item.getAmount(),
                            "Reversal of " + item.getTransactionId() + " (" + type + ")", item.getTransactionId()));
                }
                return commit(affected, reversals);
            } catch (RuntimeException ex) {
                oldBalances.forEach(Account::restoreBalanceAfterFailedCommit);
                throw ex;
            }
        });
    }

    /** Posts 2% p.a. savings interest monthly. An INTEREST record prevents duplicate posting in that month. */
    public int postMonthlyInterest() {
        authenticationService.requireRole(UserRole.ADMIN);
        return postInterestForCurrentMonth();
    }

    /** Runs automatically during application startup; the month marker prevents duplicate posting. */
    public int postDueMonthlyInterestOnStartup() {
        return postInterestForCurrentMonth();
    }

    private int postInterestForCurrentMonth() {
        YearMonth month = YearMonth.now(clock);
        int[] posted = {0};
        for (Account account : accountRepository.findAll()) {
            if (!(account instanceof SavingsAccount)) continue;
            withAccountLocks(List.of(account), () -> {
                if (account.isClosed()) return null;
                boolean alreadyPosted = account.getTransactions().stream()
                        .anyMatch(t -> TYPE_INTEREST.equals(t.getTransactionType())
                                && YearMonth.from(t.getTransactionDate()).equals(month));
                if (alreadyPosted) return null;
                BigDecimal interest = interestStrategy.calculateMonthlyInterest(account.getBalance());
                if (interest.signum() <= 0) return null;
                BigDecimal previousBalance = account.getBalance();
                try {
                    account.deposit(interest);
                    commit(List.of(account), List.of(pending(account, TYPE_INTEREST, interest,
                            "Monthly savings interest for " + month, "")));
                    posted[0]++;
                } catch (RuntimeException ex) {
                    account.restoreBalanceAfterFailedCommit(previousBalance);
                    throw ex;
                }
                return null;
            });
        }
        return posted[0];
    }

    public void checkDailyLimit(Account account, BigDecimal amount) {
        withAccountLocks(List.of(account), () -> {
            checkDailyLimitUnlocked(account, amount);
            return null;
        });
    }

    private Account requireOwnedCustomerAccount(String accountNumber) {
        var user = authenticationService.requireRole(UserRole.CUSTOMER);
        String number = Validators.requireText(accountNumber, "Account number");
        Account account = accountRepository.findByAccountNumber(number)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
        if (!account.getOwner().getUserId().equals(user.getUserId())) {
            throw new UnauthorizedActionException("You do not have access to this account.");
        }
        return account;
    }

    private void checkDailyLimitUnlocked(Account account, BigDecimal amount) {
        if (account == null || amount == null) throw new IllegalArgumentException("Account and amount are required.");
        BigDecimal alreadyOut = outgoingToday(account);
        BigDecimal limit = account.getDailyWithdrawalLimit();
        if (alreadyOut.add(amount).compareTo(limit) > 0) {
            BigDecimal remaining = limit.subtract(alreadyOut).max(BigDecimal.ZERO);
            throw new DailyLimitExceededException("Daily withdrawal limit exceeded. You can still take out "
                    + remaining.toPlainString() + " today.");
        }
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

    private int monthlyWithdrawalCount(Account account, YearMonth month) {
        int count = 0;
        for (Transaction transaction : account.getTransactions()) {
            if (TYPE_WITHDRAWAL.equals(transaction.getTransactionType())
                    && YearMonth.from(transaction.getTransactionDate()).equals(month)) count++;
        }
        return count;
    }

    private Pending pending(Account account, String type, BigDecimal amount, String description, String referenceId) {
        return new Pending(account, type, amount, description, referenceId, account.getBalance());
    }

    /**
     * Commit order: write the ledger first, then replace all affected account snapshots in one file write.
     * Account transactionCount values allow startup to discard an uncommitted ledger tail after a crash.
     */
    private List<Transaction> commit(List<Account> accounts, List<Pending> pending) {
        List<Transaction> previousLedger = transactionRepository.findAll();
        Map<Account, Integer> oldTransactionSizes = new HashMap<>();
        for (Account account : accounts) oldTransactionSizes.put(account, account.getTransactions().size());

        List<Transaction> additions = new ArrayList<>();
        for (Pending item : pending) {
            Transaction transaction = new Transaction(UUID.randomUUID().toString(), item.type(), item.amount(),
                    LocalDateTime.now(clock), item.account().getAccountNumber(), item.balanceAfter(),
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

    /** Global ledger lock first, then per-account locks in lexical account-number order. */
    private <T> T withAccountLocks(Collection<Account> accounts, Supplier<T> operation) {
        ledgerLock.lock();
        List<ReentrantLock> acquired = new ArrayList<>();
        try {
            List<Account> ordered = accounts.stream().distinct()
                    .sorted(Comparator.comparing(Account::getAccountNumber)).toList();
            for (Account account : ordered) {
                ReentrantLock lock = accountLocks.computeIfAbsent(account.getAccountNumber(), ignored -> new ReentrantLock(true));
                lock.lock();
                acquired.add(lock);
            }
            return operation.get();
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) acquired.get(i).unlock();
            ledgerLock.unlock();
        }
    }

    private record Pending(Account account, String type, BigDecimal amount,
                           String description, String referenceId, BigDecimal balanceAfter) { }
}
