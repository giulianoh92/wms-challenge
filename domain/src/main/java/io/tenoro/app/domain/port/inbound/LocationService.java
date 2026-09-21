package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.Location;

import java.util.List;

/**
 * Inbound port (Use Case) for Location management operations.
 */
public interface LocationService {

    /**
     * Creates a new location.
     *
     * @param location the location to create
     * @return the created location
     * @throws io.tenoro.app.domain.exception.ConflictException if a location with the same code already exists
     */
    Location create(Location location);

    /**
     * Retrieves all locations.
     *
     * @return a list of all locations
     */
    List<Location> findAll();
}
