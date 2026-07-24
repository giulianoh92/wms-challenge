package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.User;
import io.tenoro.app.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for User persistence operations.
 * This interface defines the contract for persisting and retrieving Users
 * from external storage systems (database, file system, etc.).
 */
public interface UserRepository {

    /**
     * Saves a user to the repository.
     * If the user already exists, it will be updated.
     *
     * @param user the user to save
     * @return the saved user
     */
    User save(User user);

    /**
     * Finds a user by their ID.
     *
     * @param userId the user's ID
     * @return an Optional containing the user if found, empty otherwise
     */
    Optional<User> findById(UserId userId);

    /**
     * Finds a user by their email address.
     *
     * @param email the user's email
     * @return an Optional containing the user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Retrieves all users from the repository.
     *
     * @return a list of all users
     */
    List<User> findAll();

    /**
     * Checks if a user exists with the given ID.
     *
     * @param userId the user's ID
     * @return true if the user exists, false otherwise
     */
    boolean existsById(UserId userId);

    /**
     * Checks if a user exists with the given email.
     *
     * @param email the user's email
     * @return true if a user with this email exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Deletes a user by their ID.
     *
     * @param userId the ID of the user to delete
     * @return true if the user was deleted, false if not found
     */
    boolean deleteById(UserId userId);

    /**
     * Deletes all users from the repository.
     * Use with caution!
     */
    void deleteAll();

    /**
     * Counts the total number of users in the repository.
     *
     * @return the number of users
     */
    long count();
}
