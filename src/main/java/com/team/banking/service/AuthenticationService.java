package com.team.banking.service;

import com.team.banking.exception.AccountLockedException;
import com.team.banking.exception.AuthenticationException;
import com.team.banking.exception.UnauthorizedActionException;
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

/** Persistent PIN authentication with salted PBKDF2 hashes, account locking and role checks. */
public class AuthenticationService {
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 6;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final String LOGIN_ERROR = "Invalid user ID/email or PIN.";

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();
    private User currentUser;

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public synchronized void register(User user, String pin) {
        register(user, pin, false);
    }

    public synchronized void register(User user, String pin, boolean forcePinChange) {
        if (user == null) throw new IllegalArgumentException("User is required.");
        validatePin(pin);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        AuthCredential credential = new AuthCredential(encode(salt), encode(hashPin(pin, salt)),
                0, false, forcePinChange);
        userRepository.save(user, credential);
    }

    /** Accepts a stable user ID or email address as the login identifier. */
    public synchronized User login(String identifier, String pin) {
        if (identifier == null || pin == null) throw new AuthenticationException(LOGIN_ERROR);
        User user = userRepository.findById(identifier.trim())
                .or(() -> userRepository.findByEmail(identifier.trim()))
                .orElseThrow(() -> new AuthenticationException(LOGIN_ERROR));
        AuthCredential credential = userRepository.findCredentialByUserId(user.getUserId())
                .orElseThrow(() -> new AuthenticationException(LOGIN_ERROR));

        if (credential.locked()) {
            throw new AccountLockedException("This user is locked. Contact an administrator.");
        }
        if (credential.salt().isBlank() || credential.passwordHash().isBlank()) {
            throw new AuthenticationException("No PIN is set for this profile. Contact an administrator.");
        }

        byte[] salt;
        byte[] expectedHash;
        try {
            salt = decode(credential.salt());
            expectedHash = decode(credential.passwordHash());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Stored authentication data is malformed. Contact an administrator.", ex);
        }
        byte[] actualHash = hashPin(pin, salt);
        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            int attempts = credential.failedAttempts() + 1;
            boolean locked = attempts >= MAX_FAILED_ATTEMPTS;
            userRepository.saveCredential(user.getUserId(), credential.withFailedAttempts(attempts, locked));
            if (locked) {
                throw new AccountLockedException("Too many failed attempts. Your account is now locked; contact an administrator.");
            }
            throw new AuthenticationException(LOGIN_ERROR);
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
        if (currentUser == null) throw new UnauthorizedActionException("You must log in first.");
        return currentUser;
    }

    public synchronized User requireRole(UserRole requiredRole) {
        User user = requireLogin();
        if (user.getRole() != requiredRole) {
            throw new UnauthorizedActionException("This command is available to "
                    + requiredRole.name().toLowerCase() + " users only.");
        }
        return user;
    }

    public synchronized boolean mustChangePin() {
        if (currentUser == null) return false;
        return userRepository.findCredentialByUserId(currentUser.getUserId())
                .map(AuthCredential::mustChangePassword).orElse(false);
    }

    /** Kept as a compatibility alias for older source files. */
    public synchronized boolean mustChangePassword() { return mustChangePin(); }

    public synchronized void changePin(String currentPin, String newPin) {
        User user = requireLogin();
        AuthCredential existing = userRepository.findCredentialByUserId(user.getUserId())
                .orElseThrow(() -> new IllegalStateException("Authentication data is missing."));
        if (existing.salt().isBlank() || existing.passwordHash().isBlank()) {
            throw new AuthenticationException("No current PIN is set. Ask an administrator to set one.");
        }
        byte[] salt = decode(existing.salt());
        if (!MessageDigest.isEqual(decode(existing.passwordHash()), hashPin(currentPin, salt))) {
            throw new AuthenticationException("Current PIN is incorrect.");
        }
        setPinForUser(user, newPin, false);
    }

    /** Compatibility alias; new CLI commands call changePin. */
    public synchronized void changePassword(String currentPin, String newPin) {
        changePin(currentPin, newPin);
    }

    /** Admin-only PIN reset. The user must choose a new PIN after their next login. */
    public synchronized void adminResetPin(String emailOrId, String newPin) {
        requireRole(UserRole.ADMIN);
        User user = userRepository.findById(emailOrId)
                .or(() -> userRepository.findByEmail(emailOrId))
                .orElseThrow(() -> new AuthenticationException("User not found."));
        setPinForUser(user, newPin, true);
    }

    /** Compatibility alias; retained while older integrations migrate to PIN terminology. */
    public synchronized void adminResetPassword(String email, String newPin) {
        adminResetPin(email, newPin);
    }

    public synchronized void unlockUser(String emailOrId) {
        requireRole(UserRole.ADMIN);
        User user = userRepository.findById(emailOrId)
                .or(() -> userRepository.findByEmail(emailOrId))
                .orElseThrow(() -> new AuthenticationException("User not found."));
        AuthCredential credential = userRepository.findCredentialByUserId(user.getUserId())
                .orElse(new AuthCredential("", "", 0, false, true));
        userRepository.saveCredential(user.getUserId(), credential.resetLock());
    }

    private void setPinForUser(User user, String pin, boolean forceChange) {
        validatePin(pin);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        AuthCredential previous = userRepository.findCredentialByUserId(user.getUserId())
                .orElse(new AuthCredential("", "", 0, false, forceChange));
        userRepository.saveCredential(user.getUserId(), previous.withPassword(
                encode(salt), encode(hashPin(pin, salt)), forceChange));
    }

    private void validatePin(String pin) {
        if (pin == null || !pin.matches("\\d{" + MIN_PIN_LENGTH + "," + MAX_PIN_LENGTH + "}")) {
            throw new IllegalArgumentException("PIN must contain exactly 4 to 6 numeric digits.");
        }
    }

    private byte[] hashPin(String pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PIN hashing is not available.", ex);
        } finally {
            spec.clearPassword();
        }
    }

    private String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private byte[] decode(String value) { return Base64.getDecoder().decode(value); }
}
