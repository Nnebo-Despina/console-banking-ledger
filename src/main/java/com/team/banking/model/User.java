package com.team.banking.model;

public class User {
    private String userId;
    private String fullname;
    private String email;

    public User(String userId, String fullname, String email) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID  is required");
        }
        if (fullname == null || fullname.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        this.userId = userId;
        this.fullname = fullname;
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public String getFullname() {
        return fullname;
    }

    public String getEmail() {
        return email;
    }
}