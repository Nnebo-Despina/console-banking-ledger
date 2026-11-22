package com.team.banking.repository;

import com.team.banking.model.AuthCredential;
import com.team.banking.model.User;

import java.util.List;
import java.util.Optional;

/** Persistence contract for both administrators and customers, including their authentication data. */
public interface UserRepository {
    void save(User user, AuthCredential credential);
    void saveCredential(String userId, AuthCredential credential);
    Optional<User> findById(String userId);
    Optional<User> findByEmail(String email);
    Optional<AuthCredential> findCredentialByUserId(String userId);
    List<User> findAll();
}
