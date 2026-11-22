package com.team.banking.service;

import com.team.banking.model.Customer;
import com.team.banking.model.User;
import com.team.banking.repository.UserRepository;

import java.util.List;
import java.util.UUID;

public class CustomerService {
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;

    public CustomerService(UserRepository userRepository, AuthenticationService authenticationService) {
        this.userRepository = userRepository;
        this.authenticationService = authenticationService;
    }

    public Customer registerCustomer(String fullName, String email, String pin) {
        return registerCustomer(fullName, email, pin, false);
    }

    public Customer registerCustomer(String fullName, String email, String pin, boolean forcePinChange) {
        String cleanName = Validators.requireText(fullName, "Full name");
        String cleanEmail = Validators.requireText(email, "Email");
        if (!cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Email format is not valid.");
        }
        if (userRepository.findByEmail(cleanEmail).isPresent()) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }
        Customer customer = new Customer(UUID.randomUUID().toString(), cleanName, cleanEmail);
        authenticationService.register(customer, pin, forcePinChange);
        return customer;
    }

    public Customer getById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
        if (!(user instanceof Customer customer)) {
            throw new IllegalArgumentException("The supplied user ID does not belong to a customer.");
        }
        return customer;
    }

    public Customer getByIdOrEmail(String value) {
        User user = userRepository.findById(value).or(() -> userRepository.findByEmail(value))
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
        if (!(user instanceof Customer customer)) {
            throw new IllegalArgumentException("The supplied user is not a customer.");
        }
        return customer;
    }

    public List<Customer> getAllCustomers() {
        return userRepository.findAll().stream()
                .filter(Customer.class::isInstance)
                .map(Customer.class::cast)
                .toList();
    }
}
