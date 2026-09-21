package io.tenoro.app.infra.adapter.inbound.web.mappers;

import io.tenoro.app.api.dto.location.CreateLocationRequest;
import io.tenoro.app.api.dto.location.LocationResponse;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;

public class LocationResponseMapper {

    public static LocationResponse fromDomain(Location location) {
        return LocationResponse.builder()
                .code(location.getCode())
                .type(location.getType().name())
                .build();
    }

    /**
     * type validity (PICKING/RESERVE) is enforced here, at the DTO/mapping boundary, by resolving
     * against LocationType: an unknown or missing value throws IllegalArgumentException, which the
     * scoped ReplenishmentExceptionHandler maps to 400 (SPECS.md endpoint #1 rule 2).
     */
    public static Location toDomain(CreateLocationRequest request) {
        return Location.builder()
                .code(request.getCode())
                .type(parseType(request.getType()))
                .build();
    }

    private static LocationType parseType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Location type cannot be null or blank");
        }
        try {
            return LocationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid location type: '" + type + "'. Must be PICKING or RESERVE");
        }
    }
}
