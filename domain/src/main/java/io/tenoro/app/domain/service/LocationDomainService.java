package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.domain.port.outbound.LocationRepository;

import java.util.List;

/**
 * Domain service implementation for Location management.
 */
public class LocationDomainService implements LocationService {

    private final LocationRepository locationRepository;

    public LocationDomainService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    public Location create(Location location) {
        // Business rule: Location.code must be unique (SPECS.md endpoint #1 rule 1, SRS BR-01)
        if (locationRepository.existsByCode(location.getCode())) {
            throw new ConflictException("Location with code '" + location.getCode() + "' already exists");
        }

        return locationRepository.save(location);
    }

    @Override
    public List<Location> findAll() {
        return locationRepository.findAll();
    }
}
