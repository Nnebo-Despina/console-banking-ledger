package com.team.banking.repository;

import com.team.banking.model.Customer;

import java.util.List;
import java.util.Optional;

/**
 * What the services need from "the place where customers are stored".
 * The services only know about THIS interface, never about JSON or files.
 */
public interface CustomerRepository {

    void save(Customer customer);

    Optional<Customer> findById(String userId);

    Optional<Customer> findByEmail(String email);

    List<Customer> findAll();
}
