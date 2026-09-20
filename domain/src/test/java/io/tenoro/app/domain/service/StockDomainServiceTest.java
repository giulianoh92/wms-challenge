package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.model.StockMove;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.StockMoveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit 5 unit test, no Spring context, per docs/ARCHITECTURE.md AD-09.
 * Uses hand-written in-memory fakes of InventoryRepository, LocationRepository and
 * StockMoveRepository instead of Mockito.
 */
class StockDomainServiceTest {

    private FakeInventoryRepository inventoryRepository;
    private FakeLocationRepository locationRepository;
    private FakeStockMoveRepository stockMoveRepository;
    private StockDomainService service;

    @BeforeEach
    void setUp() {
        inventoryRepository = new FakeInventoryRepository();
        locationRepository = new FakeLocationRepository();
        stockMoveRepository = new FakeStockMoveRepository();
        service = new StockDomainService(inventoryRepository, locationRepository, stockMoveRepository);
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

    // -------------------------------------------------------------------------------------------
    // moveStock
    // -------------------------------------------------------------------------------------------

    @Test
    void moveStock_ShouldDebitSourceAndCreditDestination_WhenSourceHasEnoughStock() {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("RSV-01").quantity(20).build());

        service.moveStock("SKU-100", "RSV-01", "PICK-01", 15, null);

        assertEquals(5, inventoryRepository.findBySkuAndLocationCode("SKU-100", "RSV-01").orElseThrow().getQuantity());
        assertEquals(15, inventoryRepository.findBySkuAndLocationCode("SKU-100", "PICK-01").orElseThrow().getQuantity());
    }

    @Test
    void moveStock_ShouldTreatMissingDestinationRecordAsZero() {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("RSV-01").quantity(10).build());

        service.moveStock("SKU-100", "RSV-01", "PICK-01", 4, null);

        assertFalse(inventoryRepository.findBySkuAndLocationCode("SKU-100", "PICK-01").isEmpty());
        assertEquals(4, inventoryRepository.findBySkuAndLocationCode("SKU-100", "PICK-01").orElseThrow().getQuantity());
    }

    @Test
    void moveStock_ShouldThrowConflictException_AndLeaveQuantitiesUnchanged_WhenSourceHasInsufficientStock() {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("RSV-01").quantity(5).build());

        assertThrows(ConflictException.class, () -> service.moveStock("SKU-100", "RSV-01", "PICK-01", 10, null));

        // No partial mutation: source unchanged, destination never created.
        assertEquals(5, inventoryRepository.findBySkuAndLocationCode("SKU-100", "RSV-01").orElseThrow().getQuantity());
        assertTrue(inventoryRepository.findBySkuAndLocationCode("SKU-100", "PICK-01").isEmpty());
        assertTrue(stockMoveRepository.query(null, null, null).isEmpty());
    }

    @Test
    void moveStock_ShouldThrowNotFoundException_WhenFromLocationDoesNotExist() {
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());

        assertThrows(NotFoundException.class, () -> service.moveStock("SKU-100", "RSV-99", "PICK-01", 5, null));
    }

    @Test
    void moveStock_ShouldThrowNotFoundException_WhenToLocationDoesNotExist() {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("RSV-01").quantity(5).build());

        assertThrows(NotFoundException.class, () -> service.moveStock("SKU-100", "RSV-01", "PICK-99", 5, null));
    }

    @Test
    void moveStock_ShouldThrowIllegalArgumentException_WhenQuantityIsZeroOrNegative() {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());

        assertThrows(IllegalArgumentException.class, () -> service.moveStock("SKU-100", "RSV-01", "PICK-01", 0, null));
        assertThrows(IllegalArgumentException.class, () -> service.moveStock("SKU-100", "RSV-01", "PICK-01", -3, null));
    }

    @Test
    void moveStock_ShouldThrowIllegalArgumentException_WhenFromEqualsTo() {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("RSV-01").quantity(5).build());

        assertThrows(IllegalArgumentException.class, () -> service.moveStock("SKU-100", "RSV-01", "RSV-01", 1, null));
    }

    @Test
    void moveStock_ShouldAppendExactlyOneStockMove_WithRelatedTaskIdNull_ForADirectMove() {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("RSV-01").quantity(20).build());

        StockMove move = service.moveStock("SKU-100", "RSV-01", "PICK-01", 7, null);

        List<StockMove> recorded = stockMoveRepository.query(null, null, null);
        assertEquals(1, recorded.size());
        StockMove saved = recorded.get(0);
        assertEquals(move.getId(), saved.getId());
        assertEquals("SKU-100", saved.getSku());
        assertEquals("RSV-01", saved.getFromLocation());
        assertEquals("PICK-01", saved.getToLocation());
        assertEquals(7, saved.getQuantity());
        assertNull(saved.getRelatedTaskId());
        assertNotNull(saved.getTimestamp());
        assertNotNull(saved.getId());
    }

    // -------------------------------------------------------------------------------------------
    // listMoves
    // -------------------------------------------------------------------------------------------

    @Test
    void listMoves_ShouldReturnMostRecentFirst_AndRespectFilters() throws InterruptedException {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        locationRepository.save(Location.builder().code("RSV-02").type(LocationType.RESERVE).build());
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("RSV-01").quantity(20).build());
        service.loadStock(InventoryItem.builder().sku("SKU-200").locationCode("RSV-02").quantity(20).build());

        StockMove first = service.moveStock("SKU-100", "RSV-01", "PICK-01", 5, null);
        Thread.sleep(2);
        StockMove second = service.moveStock("SKU-200", "RSV-02", "PICK-01", 3, "TASK-1");

        List<StockMove> all = service.listMoves(null, null, null);
        assertEquals(2, all.size());
        assertEquals(second.getId(), all.get(0).getId());
        assertEquals(first.getId(), all.get(1).getId());

        assertEquals(1, service.listMoves("SKU-100", null, null).size());
        assertEquals(1, service.listMoves(null, "RSV-02", null).size());
        assertEquals(1, service.listMoves(null, null, "TASK-1").size());
        assertEquals(2, service.listMoves(null, "PICK-01", null).size());
    }

    // -------------------------------------------------------------------------------------------
    // Concurrency (docs/ARCHITECTURE.md AD-02, docs/SRS.md NFR-03/NFR-04)
    // -------------------------------------------------------------------------------------------

    /**
     * Drains a reserve location of exactly N units via many concurrent single-unit moves. Without the
     * stockLock monitor shared between loadStock and moveStock, this test is expected to either lose
     * updates (final totals would not add up to N) or throw a spurious ConflictException from a stale
     * read. With the lock in place, conservation holds: reserve ends at exactly 0, picking ends at
     * exactly N.
     */
    @Test
    void moveStock_ShouldConserveQuantity_UnderConcurrentMoves() throws InterruptedException {
        locationRepository.save(Location.builder().code("RSV-01").type(LocationType.RESERVE).build());
        locationRepository.save(Location.builder().code("PICK-01").type(LocationType.PICKING).build());

        int totalUnits = 200;
        service.loadStock(InventoryItem.builder().sku("SKU-100").locationCode("RSV-01").quantity(totalUnits).build());

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUnits);
        AtomicInteger unexpectedFailures = new AtomicInteger(0);

        for (int i = 0; i < totalUnits; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.moveStock("SKU-100", "RSV-01", "PICK-01", 1, null);
                } catch (Exception e) {
                    unexpectedFailures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All concurrent moves should complete within the timeout");
        assertEquals(0, unexpectedFailures.get(), "No move of 1 unit should fail while units remain");
        assertEquals(0, inventoryRepository.findBySkuAndLocationCode("SKU-100", "RSV-01").orElseThrow().getQuantity());
        assertEquals(totalUnits, inventoryRepository.findBySkuAndLocationCode("SKU-100", "PICK-01").orElseThrow().getQuantity());
        assertEquals(totalUnits, stockMoveRepository.query("SKU-100", null, null).size());
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
        public Optional<InventoryItem> findBySkuAndLocationCode(String sku, String locationCode) {
            return Optional.ofNullable(items.get(sku + "|" + locationCode));
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

    /**
     * Hand-written in-memory fake — no Mockito, per the task's TDD instructions. Append-only, like the
     * real InMemoryStockMoveRepository (docs/SRS.md BR-11): no update/delete method exists on the port.
     */
    private static class FakeStockMoveRepository implements StockMoveRepository {
        private final CopyOnWriteArrayList<StockMove> moves = new CopyOnWriteArrayList<>();

        @Override
        public StockMove save(StockMove move) {
            moves.add(move);
            return move;
        }

        @Override
        public List<StockMove> query(String sku, String location, String relatedTaskId) {
            List<StockMove> matches = new ArrayList<>();
            for (StockMove move : moves) {
                if (sku != null && !move.getSku().equals(sku)) {
                    continue;
                }
                if (location != null
                        && !move.getFromLocation().equals(location)
                        && !move.getToLocation().equals(location)) {
                    continue;
                }
                if (relatedTaskId != null && !relatedTaskId.equals(move.getRelatedTaskId())) {
                    continue;
                }
                matches.add(move);
            }
            java.util.Collections.reverse(matches);
            return matches;
        }
    }
}
