package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.User;
import io.tenoro.app.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port (Use Case) for User management operations.
 * This interface defines the business operations that can be performed on Users.
 */
public interface UserService {

    /**
     * Creates a new user with the given name and email.
     *
     * @param name  the user's name
     * @param email the user's email
     * @return the created user
     * @throws IllegalArgumentException if name or email are invalid
     */
    User createUser(String name, String email);

    /**
     * Retrieves a user by their ID.
     *
     * @param userId the user's ID
     * @return an Optional containing the user if found, empty otherwise
     */
    Optional<User> getUserById(UserId userId);

    /**
     * Retrieves a user by their email address.
     *
     * @param email the user's email
     * @return an Optional containing the user if found, empty otherwise
     */
    Optional<User> getUserByEmail(String email);

    /**
     * Retrieves all users.
     *
     * @return a list of all users
     */
    List<User> getAllUsers();

    /**
     * Updates a user's name.
     *
     * @param userId  the ID of the user to update
     * @param newName the new name
     * @return the updated user
     * @throws IllegalArgumentException if the new name is invalid
     * @throws RuntimeException if the user is not found
     */
    User updateUserName(UserId userId, String newName);

    /**
     * Updates a user's email.
     *
     * @param userId   the ID of the user to update
     * @param newEmail the new email
     * @return the updated user
     * @throws IllegalArgumentException if the new email is invalid
     * @throws RuntimeException if the user is not found
     */
    User updateUserEmail(UserId userId, String newEmail);

    /**
     * Deletes a user by their ID.
     *
     * @param userId the ID of the user to delete
     * @return true if the user was deleted, false if not found
     */
    boolean deleteUser(UserId userId);
}
