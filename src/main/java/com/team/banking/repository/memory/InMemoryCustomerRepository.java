package com.team.banking.repository.memory;

import com.team.banking.model.Customer;
import com.team.banking.repository.CustomerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TEMPORARY stand-in so the services can run before the JSON repository exists.
 * Data lives only in memory and disappears when the program stops.
 */
public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<String, Customer> customersById = new HashMap<>();

    @Override
    public void save(Customer customer) {
        customersById.put(customer.getUserId(), customer);
    }

    @Override
    public Optional<Customer> findById(String userId) {
        return Optional.ofNullable(customersById.get(userId));
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        for (Customer customer : customersById.values()) {
            if (customer.getEmail().equalsIgnoreCase(email)) {
                return Optional.of(customer);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Customer> findAll() {
        return new ArrayList<>(customersById.values());
    }
}
