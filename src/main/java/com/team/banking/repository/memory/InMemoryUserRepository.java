package com.team.banking.repository.memory;

import com.team.banking.model.AuthCredential;
import com.team.banking.model.User;
import com.team.banking.repository.UserRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, AuthCredential> credentials = new LinkedHashMap<>();

    @Override public synchronized void save(User user, AuthCredential credential) {
        Optional<User> existing = findByEmail(user.getEmail());
        if (existing.isPresent() && !existing.get().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }
        users.put(user.getUserId(), user);
        credentials.put(user.getUserId(), credential);
    }

    @Override public synchronized void saveCredential(String userId, AuthCredential credential) {
        if (!users.containsKey(userId)) throw new IllegalArgumentException("User not found.");
        credentials.put(userId, credential);
    }

    @Override public synchronized Optional<User> findById(String userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override public synchronized Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return users.values().stream().filter(u -> u.getEmail().toLowerCase(Locale.ROOT).equals(normalized)).findFirst();
    }

    @Override public synchronized Optional<AuthCredential> findCredentialByUserId(String userId) {
        return Optional.ofNullable(credentials.get(userId));
    }

    @Override public synchronized List<User> findAll() { return new ArrayList<>(users.values()); }
}
