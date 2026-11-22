package com.team.banking.model;

public class User {
    private final String userId;
    private final String fullname;
    private final String email;
    private final UserRole role;

    public User(String userId, String fullname, String email, UserRole role) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required.");
        }
        if (fullname == null || fullname.isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (role == null) {
            throw new IllegalArgumentException("User role is required.");
        }
        this.userId = userId.trim();
        this.fullname = fullname.trim();
        this.email = email.trim();
        this.role = role;
    }

    /** Backwards-compatible constructor for existing customer code. */
    public User(String userId, String fullname, String email) {
        this(userId, fullname, email, UserRole.CUSTOMER);
    }

    public String getUserId() { return userId; }
    public String getFullname() { return fullname; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }
}
