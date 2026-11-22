package com.team.banking.model;

public final class Admin extends User {
    public Admin(String userId, String fullname, String email) {
        super(userId, fullname, email, UserRole.ADMIN);
    }
}
