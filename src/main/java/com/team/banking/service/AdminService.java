package com.team.banking.service;

import com.team.banking.model.Account;
import com.team.banking.model.Customer;
import com.team.banking.model.Transaction;
import com.team.banking.model.UserRole;
import com.team.banking.repository.TransactionRepository;

import java.util.List;

/** Operations that are available only to an authenticated administrator. */
public class AdminService {
    private final AuthenticationService authenticationService;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    public AdminService(AuthenticationService authenticationService, CustomerService customerService,
                        AccountService accountService, TransactionRepository transactionRepository,
                        TransactionService transactionService) {
        this.authenticationService = authenticationService;
        this.customerService = customerService;
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
    }

    public Customer createCustomer(String fullName, String email, String temporaryPin) {
        requireAdmin();
        return customerService.registerCustomer(fullName, email, temporaryPin, true);
    }

    public List<Customer> getCustomers() {
        requireAdmin();
        return customerService.getAllCustomers();
    }

    public Customer getCustomer(String idOrEmail) {
        requireAdmin();
        return customerService.getByIdOrEmail(idOrEmail);
    }

    public List<Account> getCustomerAccounts(String customerId) {
        requireAdmin();
        return accountService.getAccountsForCustomerAsAdmin(customerId);
    }

    public List<Account> getAllAccounts() {
        requireAdmin();
        return accountService.getAllAccountsAsAdmin();
    }

    public Account openSavingsForCustomer(String customerId, java.math.BigDecimal initialDeposit,
                                           java.math.BigDecimal dailyLimit, java.math.BigDecimal minimumBalance) {
        requireAdmin();
        return accountService.openSavingsAccountForCustomer(customerId, initialDeposit, dailyLimit, minimumBalance);
    }

    public Account openCurrentForCustomer(String customerId, java.math.BigDecimal initialDeposit,
                                           java.math.BigDecimal dailyLimit, java.math.BigDecimal overdraftLimit) {
        requireAdmin();
        return accountService.openCurrentAccountForCustomer(customerId, initialDeposit, dailyLimit, overdraftLimit);
    }

    public void unlockUser(String email) {
        requireAdmin();
        authenticationService.unlockUser(email);
    }

    public void resetPin(String emailOrId, String newPin) {
        requireAdmin();
        authenticationService.adminResetPin(emailOrId, newPin);
    }

    /** Compatibility alias for older integrations. */
    public void resetPassword(String emailOrId, String newPin) {
        resetPin(emailOrId, newPin);
    }

    public List<Transaction> reverseTransaction(String accountNumber, String transactionId) {
        requireAdmin();
        return transactionService.reverse(accountNumber, transactionId);
    }

    public int postMonthlyInterest() {
        requireAdmin();
        return transactionService.postMonthlyInterest();
    }

    public void closeAccount(String accountNumber) {
        requireAdmin();
        accountService.closeAccountAsAdmin(accountNumber);
    }

    public List<Transaction> getSystemLedger() {
        requireAdmin();
        return transactionRepository.findAll();
    }

    private void requireAdmin() {
        authenticationService.requireRole(UserRole.ADMIN);
    }
}
