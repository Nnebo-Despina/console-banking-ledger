package com.team.banking.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.team.banking.model.Customer;
import com.team.banking.repository.CustomerRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JsonCustomerRepository implements CustomerRepository {
    private final JsonFile file;
    private final Map<String, Customer> customers = new LinkedHashMap<>();

    public JsonCustomerRepository(Path usersFile) {
        this.file = new JsonFile(usersFile);
        for (JsonElement element : file.read()) {
            JsonObject row = element.getAsJsonObject();
            String id = file.required(row, "userId");
            String email = file.required(row, "email");
            if (customers.containsKey(id) || findByEmail(email).isPresent()) {
                throw file.malformed("Duplicate user ID or email address.");
            }
            customers.put(id, new Customer(id, file.required(row, "fullName"), email));
        }
    }

    @Override
    public synchronized void save(Customer customer) {
        Optional<Customer> sameEmail = findByEmail(customer.getEmail());
        if (sameEmail.isPresent() && !sameEmail.get().getUserId().equals(customer.getUserId())) {
            throw new IllegalArgumentException("A customer with this email already exists.");
        }
        Map<String, Customer> updated = new LinkedHashMap<>(customers);
        updated.put(customer.getUserId(), customer);
        JsonArray rows = new JsonArray();
        for (Customer entry : updated.values()) {
            JsonObject row = new JsonObject();
            row.addProperty("userId", entry.getUserId());
            row.addProperty("fullName", entry.getFullname());
            row.addProperty("email", entry.getEmail());
            rows.add(row);
        }
        file.write(rows);
        customers.put(customer.getUserId(), customer);
    }

    @Override
    public synchronized Optional<Customer> findById(String userId) {
        return Optional.ofNullable(customers.get(userId));
    }

    @Override
    public synchronized Optional<Customer> findByEmail(String email) {
        return customers.values().stream()
                .filter(customer -> customer.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    public synchronized List<Customer> findAll() {
        return new ArrayList<>(customers.values());
    }
}
