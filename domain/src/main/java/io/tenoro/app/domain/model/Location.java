package io.tenoro.app.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Location {
    private final String code;
    private final LocationType type;

    public Location(String code, LocationType type) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Location code cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Location type cannot be null");
        }
        this.code = code;
        this.type = type;
    }
}
