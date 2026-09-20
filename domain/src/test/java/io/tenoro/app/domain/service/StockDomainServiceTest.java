package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit 5 unit test, no Spring context, per docs/ARCHITECTURE.md AD-09.
 * Uses hand-written in-memory fakes of InventoryRepository and LocationRepository instead of Mockito.
 */
class StockDomainServiceTest {

    private FakeInventoryRepository inventoryRepository;
    private FakeLocationRepository locationRepository;
    private StockDomainService service;

    @BeforeEach
    void setUp() {
        inventoryRepository = new FakeInventoryRepository();
        locationRepository = new FakeLocationRepository();
        service = new StockDomainService(inventoryRepository, locationRepository);
    }

    @Test
    void loadStock_ShouldPersistAndReturnItem_WhenLocationExists() {
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        InventoryItem item = InventoryItem.builder().sku("SKU-100").locationCode("PICK-01").quantity(5).build();

        InventoryItem loaded = service.loadStock(item);

        assertEquals("SKU-100", loaded.getSku());
        assertEquals("PICK-01", loaded.getLocationCode());
        assertEquals(5, loaded.getQuantity());
    }

    @Test
    void loadStock_ShouldThrowNotFoundException_WhenLocationDoesNotExist() {
        InventoryItem item = InventoryItem.builder().sku("SKU-100").locationCode("PICK-99").quantity(5).build();

        assertThrows(NotFoundException.class, () -> service.loadStock(item));
    }

    @Test
    void loadStock_ShouldThrowIllegalArgumentException_WhenQuantityIsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryItem.builder().sku("SKU-100").locationCode("PICK-01").quantity(-1).build());
    }

    @Test
    void loadStock_ShouldReplaceQuantity_WhenLoadedTwiceForSameSkuAndLocation() {
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());

        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("PICK-01").quantity(5).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("PICK-01").quantity(8).build());

        List<InventoryItem> all = service.query(null, null);
        assertEquals(1, all.size());
        assertEquals(8, all.get(0).getQuantity());
    }

    @Test
    void query_BySku_ShouldReturnOnlyMatchingItems() {
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        locationRepository.save(Location.builder().code("PICK-02").type(LocationType.PICKING).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("PICK-01").quantity(5).build());
        service.loadStock(InventoryItem.builder().sku("SKU-200").locationCode("PICK-02").quantity(3).build());

        List<InventoryItem> result = service.query("SKU-100", null);

        assertEquals(1, result.size());
        assertEquals("SKU-100", result.get(0).getSku());
    }

    @Test
    void query_ByLocation_ShouldReturnOnlyMatchingItems() {
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        locationRepository.save(Location.builder().code("PICK-02").type(LocationType.PICKING).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("PICK-01").quantity(5).build());
        service.loadStock(InventoryItem.builder().sku("SKU-200").locationCode("PICK-02").quantity(3).build());

        List<InventoryItem> result = service.query(null, "PICK-02");

        assertEquals(1, result.size());
        assertEquals("PICK-02", result.get(0).getLocationCode());
    }

    /**
     * Hand-written in-memory fake — no Mockito, per the task's TDD instructions.
     */
    private static class FakeInventoryRepository implements InventoryRepository {
        private final Map<String, InventoryItem> items = new ConcurrentHashMap<>();

        @Override
        public InventoryItem upsert(InventoryItem item) {
            items.put(item.getSku() + "|" + item.getLocationCode(), item);
            return item;
        }

        @Override
        public List<InventoryItem> findBySku(String sku) {
            return items.values().stream().filter(i -> i.getSku().equals(sku)).toList();
        }

        @Override
        public List<InventoryItem> findByLocationCode(String locationCode) {
            return items.values().stream().filter(i -> i.getLocationCode().equals(locationCode)).toList();
        }

        @Override
        public List<InventoryItem> findAll() {
            return new ArrayList<>(items.values());
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
