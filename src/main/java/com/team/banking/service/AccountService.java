package com.team.banking.service;

import com.team.banking.model.Account;
import com.team.banking.model.AccountFactory;
import com.team.banking.model.Customer;
import com.team.banking.model.Transaction;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.CustomerRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AuthenticationService authenticationService;

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          AuthenticationService authenticationService) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.authenticationService = authenticationService;
    }

    public Account openSavingsAccount(BigDecimal initialDeposit,
                                      BigDecimal dailyWithdrawalLimit,
                                      BigDecimal minimumBalance) {
        Customer owner = authenticationService.requireLogin();

        Validators.requireNonNegativeMoney(initialDeposit, "Initial deposit");
        Validators.requirePositiveMoney(dailyWithdrawalLimit, "Daily withdrawal limit");
        Validators.requireNonNegativeMoney(minimumBalance, "Minimum balance");
        if (initialDeposit.compareTo(minimumBalance) < 0) {
            throw new IllegalArgumentException(
                    "Initial deposit must be at least the minimum balance.");
        }

        Account account = AccountFactory.createSavingsAccount(
                owner, initialDeposit, dailyWithdrawalLimit, minimumBalance);
        return store(owner, account, initialDeposit);
    }

    public Account openCurrentAccount(BigDecimal initialDeposit,
                                      BigDecimal dailyWithdrawalLimit,
                                      BigDecimal overdraftLimit) {
        Customer owner = authenticationService.requireLogin();

        Validators.requireNonNegativeMoney(initialDeposit, "Initial deposit");
        Validators.requirePositiveMoney(dailyWithdrawalLimit, "Daily withdrawal limit");
        Validators.requireNonNegativeMoney(overdraftLimit, "Overdraft limit");

        Account account = AccountFactory.createCurrentAccount(
                owner, initialDeposit, dailyWithdrawalLimit, overdraftLimit);
        return store(owner, account, initialDeposit);
    }

    /** Finds any account by number (no ownership check). */
    public Account getAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number is required.");
        }
        return accountRepository.findByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
    }

    /** Finds an account and makes sure it belongs to the logged-in customer. */
    public Account getOwnedAccount(String accountNumber) {
        Customer user = authenticationService.requireLogin();
        Account account = getAccount(accountNumber);

        if (!account.getOwner().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("You do not have access to this account.");
        }
        return account;
    }

    public List<Account> getMyAccounts() {
        Customer user = authenticationService.requireLogin();
        return accountRepository.findByOwnerId(user.getUserId());
    }

    public BigDecimal getBalance(String accountNumber) {
        return getOwnedAccount(accountNumber).getBalance();
    }

    private Account store(Customer owner, Account account, BigDecimal initialDeposit) {
        if (initialDeposit.signum() > 0) {
            // Record the starting money so the statement adds up to the balance.
            account.getTransactions().add(new Transaction(
                    UUID.randomUUID().toString(),
                    TransactionService.TYPE_OPENING_DEPOSIT,
                    initialDeposit,
                    LocalDateTime.now()));
        }
        owner.addAccount(account);
        accountRepository.save(account);
        customerRepository.save(owner);
        return account;
    }
}
