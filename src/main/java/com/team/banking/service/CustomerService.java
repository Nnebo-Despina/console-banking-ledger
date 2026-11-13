package com.team.banking.service;

import com.team.banking.model.Customer;
import com.team.banking.repository.CustomerRepository;

import java.util.List;
import java.util.UUID;

public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuthenticationService authenticationService;

    public CustomerService(CustomerRepository customerRepository,
                           AuthenticationService authenticationService) {
        this.customerRepository = customerRepository;
        this.authenticationService = authenticationService;
    }

    public Customer registerCustomer(String fullName, String email, String password) {
        String cleanName = Validators.requireText(fullName, "Full name");
        String cleanEmail = Validators.requireText(email, "Email");

        if (!cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Email format is not valid.");
        }
        if (customerRepository.findByEmail(cleanEmail).isPresent()) {
            throw new IllegalArgumentException("A customer with this email already exists.");
        }

        Customer customer = new Customer(UUID.randomUUID().toString(), cleanName, cleanEmail);

        // Password is checked and stored BEFORE saving the customer, so a bad
        // password cannot leave behind a customer who can never log in.
        authenticationService.register(customer, password);
        customerRepository.save(customer);

        return customer;
    }

    public Customer getById(String userId) {
        return customerRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
    }

    public Customer getByEmail(String email) {
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
}
