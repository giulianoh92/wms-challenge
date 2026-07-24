package io.tenoro.app.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.Objects;
import java.util.UUID;
@Data
@Builder
public class UserId {
    private final String value;

    private UserId(String value) {
        this.value = Objects.requireNonNull(value, "UserId value cannot be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("UserId value cannot be empty");
        }
    }

    public static UserId of(String value) {
        return new UserId(value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }
}
