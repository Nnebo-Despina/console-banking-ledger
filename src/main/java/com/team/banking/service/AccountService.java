package com.team.banking.service;

import com.team.banking.model.Account;
import com.team.banking.model.AccountFactory;
import com.team.banking.model.Customer;
import com.team.banking.repository.AccountRepository;
import com.team.banking.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;

public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final TransactionService transactionService;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository,
                          AuthenticationService authenticationService, TransactionService transactionService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.authenticationService = authenticationService;
        this.transactionService = transactionService;
    }

    public Account openSavingsAccount(BigDecimal initialDeposit, BigDecimal dailyWithdrawalLimit,
                                      BigDecimal minimumBalance) {
        Customer owner = (Customer) authenticationService.requireRole(com.team.banking.model.UserRole.CUSTOMER);
        return openSavingsAccountFor(owner, initialDeposit, dailyWithdrawalLimit, minimumBalance);
    }

    public Account openCurrentAccount(BigDecimal initialDeposit, BigDecimal dailyWithdrawalLimit,
                                      BigDecimal overdraftLimit) {
        Customer owner = (Customer) authenticationService.requireRole(com.team.banking.model.UserRole.CUSTOMER);
        return openCurrentAccountFor(owner, initialDeposit, dailyWithdrawalLimit, overdraftLimit);
    }

    public Account openSavingsAccountForCustomer(String customerId, BigDecimal initialDeposit,
                                                  BigDecimal dailyWithdrawalLimit, BigDecimal minimumBalance) {
        authenticationService.requireRole(com.team.banking.model.UserRole.ADMIN);
        Customer owner = findCustomer(customerId);
        return openSavingsAccountFor(owner, initialDeposit, dailyWithdrawalLimit, minimumBalance);
    }

    public Account openCurrentAccountForCustomer(String customerId, BigDecimal initialDeposit,
                                                  BigDecimal dailyWithdrawalLimit, BigDecimal overdraftLimit) {
        authenticationService.requireRole(com.team.banking.model.UserRole.ADMIN);
        Customer owner = findCustomer(customerId);
        return openCurrentAccountFor(owner, initialDeposit, dailyWithdrawalLimit, overdraftLimit);
    }

    private Account openSavingsAccountFor(Customer owner, BigDecimal initialDeposit,
                                          BigDecimal dailyWithdrawalLimit, BigDecimal minimumBalance) {
        Validators.requireNonNegativeMoney(initialDeposit, "Initial deposit");
        Validators.requirePositiveMoney(dailyWithdrawalLimit, "Daily withdrawal limit");
        Validators.requireNonNegativeMoney(minimumBalance, "Minimum balance");
        if (initialDeposit.compareTo(minimumBalance) < 0) {
            throw new IllegalArgumentException("Initial deposit must be at least the minimum balance.");
        }
        Account account = AccountFactory.createSavingsAccount(owner, initialDeposit, dailyWithdrawalLimit, minimumBalance);
        store(owner, account, initialDeposit);
        return account;
    }

    private Account openCurrentAccountFor(Customer owner, BigDecimal initialDeposit,
                                          BigDecimal dailyWithdrawalLimit, BigDecimal overdraftLimit) {
        Validators.requireNonNegativeMoney(initialDeposit, "Initial deposit");
        Validators.requirePositiveMoney(dailyWithdrawalLimit, "Daily withdrawal limit");
        Validators.requireNonNegativeMoney(overdraftLimit, "Overdraft limit");
        Account account = AccountFactory.createCurrentAccount(owner, initialDeposit, dailyWithdrawalLimit, overdraftLimit);
        store(owner, account, initialDeposit);
        return account;
    }

    private void store(Customer owner, Account account, BigDecimal initialDeposit) {
        owner.addAccount(account);
        try {
            if (initialDeposit.signum() > 0) {
                transactionService.recordOpeningDeposit(account, initialDeposit);
            } else {
                transactionService.persistNewAccount(account);
            }
        } catch (RuntimeException ex) {
            owner.removeAccount(account.getAccountNumber());
            throw ex;
        }
    }

    private Customer findCustomer(String customerId) {
        String id = Validators.requireText(customerId, "Customer ID");
        return userRepository.findById(id)
                .filter(Customer.class::isInstance)
                .map(Customer.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
    }

    /** Finds any existing account; callers must apply the appropriate access check. */
    public Account getAccount(String accountNumber) {
        String number = Validators.requireText(accountNumber, "Account number");
        return accountRepository.findByAccountNumber(number)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
    }

    public Account getOwnedAccount(String accountNumber) {
        var user = authenticationService.requireRole(com.team.banking.model.UserRole.CUSTOMER);
        Account account = getAccount(accountNumber);
        if (!account.getOwner().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("You do not have access to this account.");
        }
        return account;
    }

    public List<Account> getMyAccounts() {
        var user = authenticationService.requireRole(com.team.banking.model.UserRole.CUSTOMER);
        return accountRepository.findByOwnerId(user.getUserId());
    }

    public BigDecimal getBalance(String accountNumber) {
        return getOwnedAccount(accountNumber).getBalance();
    }

    public void closeAccountAsAdmin(String accountNumber) {
        authenticationService.requireRole(com.team.banking.model.UserRole.ADMIN);
        Account account = getAccount(accountNumber);
        transactionService.closeAccount(account);
    }

    public List<Account> getAccountsForCustomerAsAdmin(String customerId) {
        authenticationService.requireRole(com.team.banking.model.UserRole.ADMIN);
        return accountRepository.findByOwnerId(customerId);
    }

    public List<Account> getAllAccountsAsAdmin() {
        authenticationService.requireRole(com.team.banking.model.UserRole.ADMIN);
        return accountRepository.findAll();
    }
}
