package com.team.banking.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.team.banking.model.Admin;
import com.team.banking.model.AuthCredential;
import com.team.banking.model.Customer;
import com.team.banking.model.User;
import com.team.banking.model.UserRole;
import com.team.banking.repository.UserRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** JSON user store. User identity and password metadata are committed together in users.json. */
public final class JsonUserRepository implements UserRepository {
    private final JsonFile file;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, AuthCredential> credentials = new LinkedHashMap<>();

    public JsonUserRepository(Path path) {
        this.file = new JsonFile(path);
        for (JsonElement element : file.read()) {
            JsonObject row = element.getAsJsonObject();
            String id = file.required(row, "userId");
            String name = file.required(row, "fullName");
            String email = file.required(row, "email");
            UserRole role = row.has("role")
                    ? parseRole(row.get("role").getAsString()) : UserRole.CUSTOMER;
            User user = role == UserRole.ADMIN
                    ? new Admin(id, name, email)
                    : new Customer(id, name, email);
            if (users.containsKey(id) || findByEmail(email).isPresent()) {
                throw file.malformed("Duplicate user ID or email address.");
            }
            users.put(id, user);

            // Old users.json files did not contain credentials. Such accounts remain in the
            // customer list but cannot log in until an administrator sets a new password.
            String salt = optional(row, "salt", "");
            String hash = optional(row, "passwordHash", "");
            int attempts = parseInt(optional(row, "failedAttempts", "0"), "failedAttempts");
            boolean locked = Boolean.parseBoolean(optional(row, "locked", "false"));
            boolean forceChange = Boolean.parseBoolean(optional(row, "mustChangePassword", "true"));
            credentials.put(id, new AuthCredential(salt, hash, attempts, locked, forceChange));
        }
    }

    private UserRole parseRole(String value) {
        try {
            return UserRole.valueOf(value);
        } catch (RuntimeException ex) {
            throw file.malformed("Unknown user role. Expected ADMIN or CUSTOMER.");
        }
    }

    private int parseInt(String value, String field) {
        try {
            int number = Integer.parseInt(value);
            if (number < 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException ex) {
            throw file.malformed("Invalid number in field '" + field + "'.");
        }
    }

    private String optional(JsonObject row, String field, String fallback) {
        return row.has(field) && !row.get(field).isJsonNull()
                ? row.get(field).getAsString() : fallback;
    }

    @Override
    public synchronized void save(User user, AuthCredential credential) {
        if (user == null || credential == null) {
            throw new IllegalArgumentException("User and credentials are required.");
        }
        Optional<User> sameEmail = findByEmail(user.getEmail());
        if (sameEmail.isPresent() && !sameEmail.get().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }
        Map<String, User> updatedUsers = new LinkedHashMap<>(users);
        Map<String, AuthCredential> updatedCredentials = new LinkedHashMap<>(credentials);
        updatedUsers.put(user.getUserId(), user);
        updatedCredentials.put(user.getUserId(), credential);
        write(updatedUsers, updatedCredentials);
        users.clear();
        users.putAll(updatedUsers);
        credentials.clear();
        credentials.putAll(updatedCredentials);
    }

    @Override
    public synchronized void saveCredential(String userId, AuthCredential credential) {
        if (!users.containsKey(userId)) {
            throw new IllegalArgumentException("User not found.");
        }
        Map<String, AuthCredential> updated = new LinkedHashMap<>(credentials);
        updated.put(userId, credential);
        write(users, updated);
        credentials.clear();
        credentials.putAll(updated);
    }

    private void write(Map<String, User> userRows, Map<String, AuthCredential> credentialRows) {
        JsonArray rows = new JsonArray();
        for (User user : userRows.values()) {
            AuthCredential credential = credentialRows.get(user.getUserId());
            JsonObject row = new JsonObject();
            row.addProperty("userId", user.getUserId());
            row.addProperty("fullName", user.getFullname());
            row.addProperty("email", user.getEmail());
            row.addProperty("role", user.getRole().name());
            row.addProperty("salt", credential == null ? "" : credential.salt());
            row.addProperty("passwordHash", credential == null ? "" : credential.passwordHash());
            row.addProperty("failedAttempts", Integer.toString(credential == null ? 0 : credential.failedAttempts()));
            row.addProperty("locked", Boolean.toString(credential != null && credential.locked()));
            row.addProperty("mustChangePassword", Boolean.toString(credential != null && credential.mustChangePassword()));
            rows.add(row);
        }
        file.write(rows);
    }

    @Override
    public synchronized Optional<User> findById(String userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public synchronized Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String sought = email.trim().toLowerCase(Locale.ROOT);
        return users.values().stream()
                .filter(user -> user.getEmail().toLowerCase(Locale.ROOT).equals(sought))
                .findFirst();
    }

    @Override
    public synchronized Optional<AuthCredential> findCredentialByUserId(String userId) {
        return Optional.ofNullable(credentials.get(userId));
    }

    @Override
    public synchronized List<User> findAll() {
        return new ArrayList<>(users.values());
    }
}
