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

    public AdminService(AuthenticationService authenticationService, CustomerService customerService,
                        AccountService accountService, TransactionRepository transactionRepository) {
        this.authenticationService = authenticationService;
        this.customerService = customerService;
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
    }

    public Customer createCustomer(String fullName, String email, String temporaryPassword) {
        requireAdmin();
        return customerService.registerCustomer(fullName, email, temporaryPassword, true);
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

    public void resetPassword(String email, String newPassword) {
        requireAdmin();
        authenticationService.adminResetPassword(email, newPassword);
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
