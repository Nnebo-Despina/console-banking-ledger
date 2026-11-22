package com.team.banking.service;

import com.team.banking.model.Admin;
import com.team.banking.model.AuthCredential;
import com.team.banking.model.User;
import com.team.banking.model.UserRole;
import com.team.banking.repository.UserRepository;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/** Authentication with persistent salted PBKDF2 hashes, three-attempt locking and role checks. */
public class AuthenticationService {
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final String LOGIN_ERROR = "Invalid email or password.";

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();
    private User currentUser;

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public synchronized void register(User user, String password) {
        register(user, password, false);
    }

    public synchronized void register(User user, String password, boolean forcePasswordChange) {
        if (user == null) throw new IllegalArgumentException("User is required.");
        validateNewPassword(password);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        AuthCredential credential = new AuthCredential(encode(salt), encode(hashPassword(password, salt)),
                0, false, forcePasswordChange);
        userRepository.save(user, credential);
    }

    public synchronized User login(String email, String password) {
        if (email == null || password == null) throw new IllegalArgumentException(LOGIN_ERROR);
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException(LOGIN_ERROR));
        AuthCredential credential = userRepository.findCredentialByUserId(user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(LOGIN_ERROR));

        if (credential.locked()) {
            throw new IllegalStateException("This user is locked. Contact an administrator.");
        }
        // Legacy profiles have no stored password hash; the administrator must set one.
        if (credential.salt().isBlank() || credential.passwordHash().isBlank()) {
            throw new IllegalArgumentException("No password is set for this profile. Contact an administrator.");
        }

        byte[] salt;
        byte[] expectedHash;
        try {
            salt = decode(credential.salt());
            expectedHash = decode(credential.passwordHash());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Stored credentials are malformed. Contact an administrator.", ex);
        }
        byte[] actualHash = hashPassword(password, salt);
        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            int attempts = credential.failedAttempts() + 1;
            boolean locked = attempts >= MAX_FAILED_ATTEMPTS;
            userRepository.saveCredential(user.getUserId(), credential.withFailedAttempts(attempts, locked));
            if (locked) {
                throw new IllegalStateException("Too many failed attempts. Your account is now locked; contact an administrator.");
            }
            throw new IllegalArgumentException(LOGIN_ERROR);
        }

        if (credential.failedAttempts() != 0) {
            userRepository.saveCredential(user.getUserId(), credential.resetLock());
        }
        currentUser = user;
        return user;
    }

    public synchronized void logout() { currentUser = null; }
    public synchronized boolean isLoggedIn() { return currentUser != null; }
    public synchronized User getCurrentUser() { return currentUser; }

    public synchronized User requireLogin() {
        if (currentUser == null) throw new IllegalStateException("You must log in first.");
        return currentUser;
    }

    public synchronized User requireRole(UserRole requiredRole) {
        User user = requireLogin();
        if (user.getRole() != requiredRole) {
            throw new IllegalStateException("This command is available to " + requiredRole.name().toLowerCase() + " users only.");
        }
        return user;
    }

    public synchronized boolean mustChangePassword() {
        if (currentUser == null) return false;
        return userRepository.findCredentialByUserId(currentUser.getUserId())
                .map(AuthCredential::mustChangePassword).orElse(false);
    }

    public synchronized void changePassword(String currentPassword, String newPassword) {
        User user = requireLogin();
        AuthCredential existing = userRepository.findCredentialByUserId(user.getUserId())
                .orElseThrow(() -> new IllegalStateException("Authentication data is missing."));
        if (existing.salt().isBlank() || existing.passwordHash().isBlank()) {
            throw new IllegalStateException("No current password is set. Ask an administrator to set one.");
        }
        byte[] salt = decode(existing.salt());
        if (!MessageDigest.isEqual(decode(existing.passwordHash()), hashPassword(currentPassword, salt))) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        setPasswordForUser(user, newPassword, false);
    }

    /** Admin-only password reset. The user is required to choose a new password at next login. */
    public synchronized void adminResetPassword(String email, String newPassword) {
        requireRole(UserRole.ADMIN);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        setPasswordForUser(user, newPassword, true);
    }

    public synchronized void unlockUser(String email) {
        requireRole(UserRole.ADMIN);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        AuthCredential credential = userRepository.findCredentialByUserId(user.getUserId())
                .orElse(new AuthCredential("", "", 0, false, true));
        userRepository.saveCredential(user.getUserId(), credential.resetLock());
    }

    private void setPasswordForUser(User user, String password, boolean forceChange) {
        validateNewPassword(password);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        AuthCredential previous = userRepository.findCredentialByUserId(user.getUserId())
                .orElse(new AuthCredential("", "", 0, false, forceChange));
        userRepository.saveCredential(user.getUserId(), previous.withPassword(
                encode(salt), encode(hashPassword(password, salt)), forceChange));
    }

    private void validateNewPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    private byte[] hashPassword(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Password hashing is not available.", ex);
        } finally {
            spec.clearPassword();
        }
    }

    private String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private byte[] decode(String value) { return Base64.getDecoder().decode(value); }
}
