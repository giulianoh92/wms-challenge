package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit 5 unit test, no Spring context, per docs/ARCHITECTURE.md AD-09.
 * Uses a hand-written in-memory fake of LocationRepository instead of Mockito.
 */
class LocationDomainServiceTest {

    private FakeLocationRepository repository;
    private LocationDomainService service;

    @BeforeEach
    void setUp() {
        repository = new FakeLocationRepository();
        service = new LocationDomainService(repository);
    }

    @Test
    void create_ShouldPersistAndReturnLocation_WhenCodeIsUnique() {
        Location location = Location.builder().code("PICK-01").type(LocationType.PICKING).build();

        Location created = service.create(location);

        assertEquals("PICK-01", created.getCode());
        assertEquals(LocationType.PICKING, created.getType());
        assertTrue(repository.existsByCode("PICK-01"));
    }

    @Test
    void create_ShouldThrowConflictException_WhenCodeAlreadyExists() {
        service.create(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());

        Location duplicate = Location.builder().code("RSV-01").type(LocationType.RESERVE).build();

        assertThrows(ConflictException.class, () -> service.create(duplicate));
    }

    @Test
    void findAll_ShouldReturnEveryCreatedLocation() {
        service.create(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        service.create(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());

        List<Location> all = service.findAll();

        assertEquals(2, all.size());
    }

    /**
     * Hand-written in-memory fake — no Mockito, per the task's TDD instructions.
     */
    private static class FakeLocationRepository implements LocationRepository {
        private final List<Location> locations = new ArrayList<>();

        @Override
        public boolean existsByCode(String code) {
            return locations.stream().anyMatch(location -> location.getCode().equals(code));
        }

        @Override
        public Location save(Location location) {
            locations.add(location);
            return location;
        }

        @Override
        public List<Location> findAll() {
            return new ArrayList<>(locations);
        }
    }
}
