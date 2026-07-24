package io.tenoro.app.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Objects;

@Data
@Builder
public class User {
    private final UserId id;
    private final String name;
    private final String email;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public User(UserId id, String name, String email, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "User ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");

        validateEmail(email);
        validateName(name);
    }

    public static User create(String name, String email) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UserId.generate(), name, email, now, now);
    }

    public User updateName(String newName) {
        validateName(newName);
        return new User(this.id, newName, this.email, this.createdAt, LocalDateTime.now());
    }

    public User updateEmail(String newEmail) {
        validateEmail(newEmail);
        return new User(this.id, this.name, newEmail, this.createdAt, LocalDateTime.now());
    }

    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Name cannot be longer than 100 characters");
        }
    }
}
