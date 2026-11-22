package com.team.banking.model;

/** Persistent authentication data. The password itself is never stored. */
public record AuthCredential(
        String salt,
        String passwordHash,
        int failedAttempts,
        boolean locked,
        boolean mustChangePassword) {

    public AuthCredential {
        if (salt == null || passwordHash == null) {
            throw new IllegalArgumentException("Credential data cannot be null.");
        }
        if (failedAttempts < 0) {
            throw new IllegalArgumentException("Failed-attempt count cannot be negative.");
        }
    }

    public AuthCredential withFailedAttempts(int value, boolean isLocked) {
        return new AuthCredential(salt, passwordHash, value, isLocked, mustChangePassword);
    }

    public AuthCredential withPassword(String newSalt, String newHash, boolean forceChange) {
        return new AuthCredential(newSalt, newHash, 0, false, forceChange);
    }

    public AuthCredential resetLock() {
        return new AuthCredential(salt, passwordHash, 0, false, mustChangePassword);
    }
}
