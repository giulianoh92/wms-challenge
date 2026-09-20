package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit 5 unit test, no Spring context, per docs/ARCHITECTURE.md AD-09.
 * Uses hand-written in-memory fakes of ReplenishmentRuleRepository and LocationRepository instead of
 * Mockito, same pattern as LocationDomainServiceTest.
 */
class ReplenishmentRuleDomainServiceTest {

    private FakeReplenishmentRuleRepository ruleRepository;
    private FakeLocationRepository locationRepository;
    private ReplenishmentRuleDomainService service;

    @BeforeEach
    void setUp() {
        ruleRepository = new FakeReplenishmentRuleRepository();
        locationRepository = new FakeLocationRepository();
        service = new ReplenishmentRuleDomainService(ruleRepository, locationRepository);
    }

    @Test
    void create_ShouldPersistAndReturnRule_WhenLocationIsPicking() {
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        ReplenishmentRule rule = ReplenishmentRule.builder()
                .sku("SKU-100").locationCode("PICK-01").min(20).max(100).build();

        ReplenishmentRule created = service.create(rule);

        assertEquals("SKU-100", created.getSku());
        assertEquals("PICK-01", created.getLocationCode());
        assertEquals(20, created.getMin());
        assertEquals(100, created.getMax());
        assertTrue(ruleRepository.existsBySkuAndLocationCode("SKU-100", "PICK-01"));
    }

    @Test
    void create_ShouldThrowNotFoundException_WhenLocationDoesNotExist() {
        ReplenishmentRule rule = ReplenishmentRule.builder()
                .sku("SKU-100").locationCode("PICK-99").min(20).max(100).build();

        assertThrows(NotFoundException.class, () -> service.create(rule));
    }

    @Test
    void create_ShouldThrowIllegalArgumentException_WhenLocationIsNotPicking() {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        ReplenishmentRule rule = ReplenishmentRule.builder()
                .sku("SKU-100").locationCode("RSV-01").min(20).max(100).build();

        assertThrows(IllegalArgumentException.class, () -> service.create(rule));
    }

    @Test
    void create_ShouldThrowIllegalArgumentException_WhenMinIsGreaterThanMax() {
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());

        assertThrows(IllegalArgumentException.class, () -> service.create(
                ReplenishmentRule.builder().sku("SKU-100").locationCode("PICK-01").min(50).max(20).build()));
    }

    @Test
    void create_ShouldThrowIllegalArgumentException_WhenMinIsNegative() {
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());

        assertThrows(IllegalArgumentException.class, () -> service.create(
                ReplenishmentRule.builder().sku("SKU-100").locationCode("PICK-01").min(-1).max(20).build()));
    }

    @Test
    void create_ShouldThrowConflictException_WhenRuleAlreadyExistsForSameSkuAndLocation() {
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        service.create(ReplenishmentRule.builder().sku("SKU-100").locationCode("PICK-01").min(10).max(50).build());

        ReplenishmentRule duplicate = ReplenishmentRule.builder()
                .sku("SKU-100").locationCode("PICK-01").min(5).max(30).build();

        assertThrows(ConflictException.class, () -> service.create(duplicate));
    }

    /**
     * Hand-written in-memory fake — no Mockito, per the task's TDD instructions.
     */
    private static class FakeReplenishmentRuleRepository implements ReplenishmentRuleRepository {
        private final List<ReplenishmentRule> rules = new ArrayList<>();

        @Override
        public boolean existsBySkuAndLocationCode(String sku, String locationCode) {
            return rules.stream().anyMatch(rule ->
                    rule.getSku().equals(sku) && rule.getLocationCode().equals(locationCode));
        }

        @Override
        public ReplenishmentRule save(ReplenishmentRule rule) {
            rules.add(rule);
            return rule;
        }

        @Override
        public List<ReplenishmentRule> findAll() {
            return new ArrayList<>(rules);
        }
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
        public Optional<Location> findByCode(String code) {
            return locations.stream().filter(location -> location.getCode().equals(code)).findFirst();
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
