package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryLocationRepository implements LocationRepository {

    private final Map<String, Location> locations = new ConcurrentHashMap<>();

    @Override
    public boolean existsByCode(String code) {
        return locations.containsKey(code);
    }

    @Override
    public Optional<Location> findByCode(String code) {
        return Optional.ofNullable(locations.get(code));
    }

    @Override
    public Location save(Location location) {
        locations.put(location.getCode(), location);
        return location;
    }

    @Override
    public List<Location> findAll() {
        return new ArrayList<>(locations.values());
    }
}
