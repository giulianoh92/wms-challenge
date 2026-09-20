package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.Location;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for Location persistence operations.
 */
public interface LocationRepository {

    /**
     * Checks whether a location exists with the given code.
     *
     * @param code the location's code
     * @return true if a location with this code exists, false otherwise
     */
    boolean existsByCode(String code);

    /**
     * Retrieves the location with the given code, if any.
     *
     * @param code the location's code
     * @return the matching location, or empty if no location exists with this code
     */
    Optional<Location> findByCode(String code);

    /**
     * Saves a location to the repository.
     *
     * @param location the location to save
     * @return the saved location
     */
    Location save(Location location);

    /**
     * Retrieves all locations from the repository.
     *
     * @return a list of all locations
     */
    List<Location> findAll();
}
