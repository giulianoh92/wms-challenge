package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.model.ReplenishmentEvaluationResult;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskStatus;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit 5 unit test, no Spring context, per docs/ARCHITECTURE.md AD-09. Uses hand-written
 * in-memory fakes of the outbound ports instead of Mockito, same pattern as
 * ReplenishmentRuleDomainServiceTest/StockDomainServiceTest. Covers docs/SRS.md §3.5 and D1-D5.
 */
class ReplenishmentTaskDomainServiceTest {

    private FakeReplenishmentTaskRepository taskRepository;
    private FakeReplenishmentRuleRepository ruleRepository;
    private FakeInventoryRepository inventoryRepository;
    private FakeLocationRepository locationRepository;
    private ReplenishmentTaskDomainService service;

    @BeforeEach
    void setUp() {
        taskRepository = new FakeReplenishmentTaskRepository();
        ruleRepository = new FakeReplenishmentRuleRepository();
        inventoryRepository = new FakeInventoryRepository();
        locationRepository = new FakeLocationRepository();
        service = new ReplenishmentTaskDomainService(taskRepository, ruleRepository, inventoryRepository, locationRepository);
    }

    private void givenPickingLocation(String code) {
        locationRepository.save(Location.builder().code(code).type(LocationType.PICKING).build());
    }

    private void givenReserveLocation(String code) {
        locationRepository.save(Location.builder().code(code).type(LocationType.RESERVE).build());
    }

    private void givenRule(String sku, String locationCode, int min, int max) {
        ruleRepository.save(ReplenishmentRule.builder().sku(sku).locationCode(locationCode).min(min).max(max).build());
    }

    private void givenStock(String sku, String locationCode, int quantity) {
        inventoryRepository.upsert(InventoryItem.builder().sku(sku).locationCode(locationCode).quantity(quantity).build());
    }

    // -------------------------------------------------------------------------------------------
    // Validation paths
    // -------------------------------------------------------------------------------------------

    @Test
    void evaluate_ShouldThrowNotFoundException_WhenLocationDoesNotExist() {
        assertThrows(NotFoundException.class, () -> service.evaluate("SKU-100", "PICK-99"));
    }

    @Test
    void evaluate_ShouldThrowIllegalArgumentException_WhenLocationIsNotPicking() {
        givenReserveLocation("RSV-01");

        assertThrows(IllegalArgumentException.class, () -> service.evaluate("SKU-100", "RSV-01"));
    }

    @Test
    void evaluate_ShouldThrowNotFoundException_WhenNoRuleExistsForSkuAndLocation() {
        givenPickingLocation("PICK-01");

        assertThrows(NotFoundException.class, () -> service.evaluate("SKU-100", "PICK-01"));
    }

    // -------------------------------------------------------------------------------------------
    // D5 — stock already at or above min
    // -------------------------------------------------------------------------------------------

    @Test
    void evaluate_ShouldReturnNoTask_WhenStockIsAlreadyAtOrAboveMin() {
        givenPickingLocation("PICK-01");
        givenRule("SKU-100", "PICK-01", 20, 100);
        givenStock("SKU-100", "PICK-01", 25);

        ReplenishmentEvaluationResult result = service.evaluate("SKU-100", "PICK-01");

        assertFalse(result.isReplenishmentNeeded());
        assertFalse(result.isFullyReplenished());
        assertTrue(result.getTasks().isEmpty());
    }

    @Test
    void evaluate_ShouldTreatMissingInventoryRecordAsZeroStock() {
        givenPickingLocation("PICK-01");
        givenReserveLocation("RSV-01");
        givenRule("SKU-100", "PICK-01", 20, 100);
        givenStock("SKU-100", "RSV-01", 100);
        // No InventoryItem record at all for SKU-100/PICK-01.

        ReplenishmentEvaluationResult result = service.evaluate("SKU-100", "PICK-01");

        assertTrue(result.isReplenishmentNeeded());
        assertEquals(1, result.getTasks().size());
        assertEquals(100, result.getTasks().get(0).getQuantity());
    }

    // -------------------------------------------------------------------------------------------
    // D3 — reserve stock availability outcomes
    // -------------------------------------------------------------------------------------------

    @Test
    void evaluate_ShouldCreateOneFullyCoveringTask_WhenSingleReserveSourceHasExactlyWhatsNeeded() {
        givenPickingLocation("PICK-01");
        givenReserveLocation("RSV-01");
        givenRule("SKU-100", "PICK-01", 20, 100);
        givenStock("SKU-100", "PICK-01", 5);
        givenStock("SKU-100", "RSV-01", 95);

        ReplenishmentEvaluationResult result = service.evaluate("SKU-100", "PICK-01");

        assertTrue(result.isReplenishmentNeeded());
        assertTrue(result.isFullyReplenished());
        assertEquals(1, result.getTasks().size());
        ReplenishmentTask task = result.getTasks().get(0);
        assertEquals("RSV-01", task.getFromLocation());
        assertEquals("PICK-01", task.getToLocation());
        assertEquals(95, task.getQuantity());
        assertEquals(ReplenishmentTaskStatus.OPEN, task.getStatus());
    }

    @Test
    void evaluate_ShouldCreateOneFullyCoveringTask_WhenSingleReserveSourceHasMoreThanNeeded() {
        givenPickingLocation("PICK-01");
        givenReserveLocation("RSV-01");
        givenRule("SKU-100", "PICK-01", 20, 100);
        givenStock("SKU-100", "PICK-01", 5);
        givenStock("SKU-100", "RSV-01", 500);

        ReplenishmentEvaluationResult result = service.evaluate("SKU-100", "PICK-01");

        assertTrue(result.isFullyReplenished());
        assertEquals(1, result.getTasks().size());
        assertEquals(95, result.getTasks().get(0).getQuantity());
    }

    @Test
    void evaluate_ShouldCreatePartialTask_WhenReserveTotalIsLessThanNeeded() {
        givenPickingLocation("PICK-02");
        givenReserveLocation("RSV-03");
        givenRule("SKU-300", "PICK-02", 30, 120);
        givenStock("SKU-300", "PICK-02", 10);
        givenStock("SKU-300", "RSV-03", 70);

        ReplenishmentEvaluationResult result = service.evaluate("SKU-300", "PICK-02");

        assertTrue(result.isReplenishmentNeeded());
        assertFalse(result.isFullyReplenished());
        assertEquals(1, result.getTasks().size());
        ReplenishmentTask task = result.getTasks().get(0);
        assertEquals("RSV-03", task.getFromLocation());
        assertEquals(70, task.getQuantity());
    }

    @Test
    void evaluate_ShouldCreateNoTask_WhenNoReserveStockExistsAtAll() {
        givenPickingLocation("PICK-01");
        givenRule("SKU-200", "PICK-01", 10, 50);
        givenStock("SKU-200", "PICK-01", 5);

        ReplenishmentEvaluationResult result = service.evaluate("SKU-200", "PICK-01");

        assertTrue(result.isReplenishmentNeeded());
        assertFalse(result.isFullyReplenished());
        assertTrue(result.getTasks().isEmpty());
    }

    @Test
    void evaluate_ShouldIgnoreReserveLocationsWithZeroQuantity() {
        givenPickingLocation("PICK-01");
        givenReserveLocation("RSV-01");
        givenRule("SKU-100", "PICK-01", 20, 100);
        givenStock("SKU-100", "PICK-01", 5);
        givenStock("SKU-100", "RSV-01", 0);

        ReplenishmentEvaluationResult result = service.evaluate("SKU-100", "PICK-01");

        assertTrue(result.isReplenishmentNeeded());
        assertFalse(result.isFullyReplenished());
        assertTrue(result.getTasks().isEmpty());
    }

    // -------------------------------------------------------------------------------------------
    // D1/D2 — multiple reserve sources, greedy descending order, one task per source (seed scenario,
    // docs/SRS.md §7 Nota: SKU-100 needs 95 in PICK-01, RSV-01 has 60, RSV-02 has 50)
    // -------------------------------------------------------------------------------------------

    @Test
    void evaluate_ShouldSelectReserveSourcesGreedilyByDescendingQuantity_AndCreateOneTaskPerSource() {
        givenPickingLocation("PICK-01");
        givenReserveLocation("RSV-01");
        givenReserveLocation("RSV-02");
        givenRule("SKU-100", "PICK-01", 20, 100);
        givenStock("SKU-100", "PICK-01", 5);
        givenStock("SKU-100", "RSV-01", 60);
        givenStock("SKU-100", "RSV-02", 50);

        ReplenishmentEvaluationResult result = service.evaluate("SKU-100", "PICK-01");

        assertTrue(result.isReplenishmentNeeded());
        assertTrue(result.isFullyReplenished());
        assertEquals(2, result.getTasks().size());

        ReplenishmentTask first = result.getTasks().get(0);
        ReplenishmentTask second = result.getTasks().get(1);
        assertEquals("RSV-01", first.getFromLocation());
        assertEquals(60, first.getQuantity());
        assertEquals("PICK-01", first.getToLocation());
        assertEquals("RSV-02", second.getFromLocation());
        assertEquals(35, second.getQuantity());
        assertEquals("PICK-01", second.getToLocation());

        // D1: never merged into a single task even though both share sku/toLocation.
        assertEquals(2, taskRepository.findAll().size());
    }

    // -------------------------------------------------------------------------------------------
    // D4 — idempotency
    // -------------------------------------------------------------------------------------------

    @Test
    void evaluate_ShouldReturnExistingOpenTasks_WithoutCreatingDuplicates_WhenCalledTwice() {
        givenPickingLocation("PICK-01");
        givenReserveLocation("RSV-01");
        givenRule("SKU-100", "PICK-01", 20, 100);
        givenStock("SKU-100", "PICK-01", 5);
        givenStock("SKU-100", "RSV-01", 95);

        ReplenishmentEvaluationResult first = service.evaluate("SKU-100", "PICK-01");
        ReplenishmentEvaluationResult second = service.evaluate("SKU-100", "PICK-01");

        assertEquals(1, taskRepository.findAll().size());
        assertEquals(1, second.getTasks().size());
        assertEquals(first.getTasks().get(0).getId(), second.getTasks().get(0).getId());
        assertTrue(second.isReplenishmentNeeded());
        assertTrue(second.isFullyReplenished());
    }

    // -------------------------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------------------------

    @Test
    void findAll_ShouldReturnEveryPersistedTask_RegardlessOfStatus() {
        ReplenishmentTask confirmed = taskRepository.save(ReplenishmentTask.builder()
                .sku("SKU-100").fromLocation("RSV-01").toLocation("PICK-01").quantity(10)
                .status(ReplenishmentTaskStatus.CONFIRMED).build());
        ReplenishmentTask cancelled = taskRepository.save(ReplenishmentTask.builder()
                .sku("SKU-200").fromLocation("RSV-02").toLocation("PICK-01").quantity(5)
                .status(ReplenishmentTaskStatus.CANCELLED).build());

        List<ReplenishmentTask> all = service.findAll();

        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(t -> t.getId().equals(confirmed.getId())));
        assertTrue(all.stream().anyMatch(t -> t.getId().equals(cancelled.getId())));
    }

    /**
     * Hand-written in-memory fake — no Mockito, per the task's TDD instructions.
     */
    private static class FakeReplenishmentTaskRepository implements ReplenishmentTaskRepository {
        private final List<ReplenishmentTask> tasks = new ArrayList<>();

        @Override
        public ReplenishmentTask save(ReplenishmentTask task) {
            tasks.add(task);
            return task;
        }

        @Override
        public List<ReplenishmentTask> findAll() {
            return new ArrayList<>(tasks);
        }

        @Override
        public List<ReplenishmentTask> findBySkuAndToLocationAndStatus(String sku, String toLocation, ReplenishmentTaskStatus status) {
            return tasks.stream()
                    .filter(task -> task.getSku().equals(sku)
                            && task.getToLocation().equals(toLocation)
                            && task.getStatus() == status)
                    .toList();
        }
    }

    /**
     * Hand-written in-memory fake — no Mockito, per the task's TDD instructions.
     */
    private static class FakeReplenishmentRuleRepository implements ReplenishmentRuleRepository {
        private final List<ReplenishmentRule> rules = new ArrayList<>();

        @Override
        public boolean existsBySkuAndLocationCode(String sku, String locationCode) {
            return findBySkuAndLocationCode(sku, locationCode).isPresent();
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

        @Override
        public Optional<ReplenishmentRule> findBySkuAndLocationCode(String sku, String locationCode) {
            return rules.stream()
                    .filter(rule -> rule.getSku().equals(sku) && rule.getLocationCode().equals(locationCode))
                    .findFirst();
        }
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
