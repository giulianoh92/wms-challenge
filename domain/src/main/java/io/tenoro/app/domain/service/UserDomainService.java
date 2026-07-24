package io.tenoro.app.domain.service;

import io.tenoro.app.domain.model.User;
import io.tenoro.app.domain.model.UserId;
import io.tenoro.app.domain.port.inbound.UserService;
import io.tenoro.app.domain.port.outbound.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Domain service implementation for User management.
 * This class contains the business logic for user operations
 * and orchestrates calls to the outbound ports.
 */
public class UserDomainService implements UserService {

    private final UserRepository userRepository;

    public UserDomainService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User createUser(String name, String email) {
        // Business rule: Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with email '" + email + "' already exists");
        }

        // Create the user using domain model factory method
        User user = User.create(name, email);

        // Persist the user
        return userRepository.save(user);
    }

    @Override
    public Optional<User> getUserById(UserId userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> getUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User updateUserName(UserId userId, String newName) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId.getValue()));

        // Use domain model method to ensure business rules are applied
        User updatedUser = existingUser.updateName(newName);

        return userRepository.save(updatedUser);
    }

    @Override
    public User updateUserEmail(UserId userId, String newEmail) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId.getValue()));

        // Business rule: Check if the new email already exists (but not for the same user)
        Optional<User> userWithNewEmail = userRepository.findByEmail(newEmail);
        if (userWithNewEmail.isPresent() && !userWithNewEmail.get().getId().equals(userId)) {
            throw new IllegalArgumentException("User with email '" + newEmail + "' already exists");
        }

        // Use domain model method to ensure business rules are applied
        User updatedUser = existingUser.updateEmail(newEmail);

        return userRepository.save(updatedUser);
    }

    @Override
    public boolean deleteUser(UserId userId) {
        return userRepository.deleteById(userId);
    }
}
