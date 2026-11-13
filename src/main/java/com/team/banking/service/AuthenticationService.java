package com.team.banking.service;

import com.team.banking.model.Customer;
import com.team.banking.repository.CustomerRepository;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationService {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 65_536;
    private static final int KEY_BITS = 256;
    private static final String LOGIN_ERROR = "Invalid email or password.";

    private final CustomerRepository customerRepository;
    private final Map<String, String> credentials = new HashMap<>(); // userId -> "salt:hash"
    private final SecureRandom random = new SecureRandom();
    private Customer currentUser;

    public AuthenticationService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** Stores a salted hash of the password for this customer. */
    public void register(Customer customer, String password) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer is required.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = hashPassword(password, salt);

        credentials.put(customer.getUserId(), encode(salt) + ":" + encode(hash));
    }

    public Customer login(String email, String password) {
        if (email == null || password == null) {
            throw new IllegalArgumentException(LOGIN_ERROR);
        }

        Customer customer = customerRepository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException(LOGIN_ERROR));

        String stored = credentials.get(customer.getUserId());
        if (stored == null) {
            throw new IllegalArgumentException(LOGIN_ERROR);
        }

        String[] parts = stored.split(":");
        byte[] salt = decode(parts[0]);
        byte[] expectedHash = decode(parts[1]);
        byte[] actualHash = hashPassword(password, salt);

        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            throw new IllegalArgumentException(LOGIN_ERROR);
        }

        currentUser = customer;
        return customer;
    }

    public void logout() {
        currentUser = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public Customer getCurrentUser() {
        return currentUser;
    }

    /** Used by other services: returns the logged-in customer or stops with an error. */
    public Customer requireLogin() {
        if (currentUser == null) {
            throw new IllegalStateException("You must log in first.");
        }
        return currentUser;
    }

    private byte[] hashPassword(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing is not available.", e);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] decode(String text) {
        return Base64.getDecoder().decode(text);
    }
}
