package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.model.ReplenishmentEvaluationResult;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskStatus;
import io.tenoro.app.domain.model.StockMove;
import io.tenoro.app.domain.port.inbound.StockService;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentTaskRepository;
import io.tenoro.app.domain.port.outbound.StockMoveRepository;
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
    private FakeStockMoveRepository stockMoveRepository;
    private StockService stockService;
    private ReplenishmentTaskDomainService service;

    @BeforeEach
    void setUp() {
        taskRepository = new FakeReplenishmentTaskRepository();
        ruleRepository = new FakeReplenishmentRuleRepository();
        inventoryRepository = new FakeInventoryRepository();
        locationRepository = new FakeLocationRepository();
        stockMoveRepository = new FakeStockMoveRepository();
        // Real StockDomainService wired against this test's own fakes (docs/ARCHITECTURE.md AD-03):
        // confirm(id) must reuse the actual atomic move logic, not a StockService test double that
        // could silently drift from what moveStock really does.
        stockService = new StockDomainService(inventoryRepository, locationRepository, stockMoveRepository);
        service = new ReplenishmentTaskDomainService(taskRepository, ruleRepository, inventoryRepository, locationRepository, stockService);
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

    // -------------------------------------------------------------------------------------------
    // confirm(id) — FR-TSK-03, §3.6, D6, BR-08/09/10
    // -------------------------------------------------------------------------------------------

    @Test
    void confirm_ShouldThrowNotFoundException_WhenTaskDoesNotExist() {
        assertThrows(NotFoundException.class, () -> service.confirm("missing-id"));
    }

    @Test
    void confirm_ShouldTransitionTaskToConfirmed_AndMoveStock_AndRecordStockMove_WhenSuccessful() {
        givenReserveLocation("RSV-01");
        givenPickingLocation("PICK-01");
        givenStock("SKU-100", "RSV-01", 60);
        ReplenishmentTask task = taskRepository.save(ReplenishmentTask.builder()
                .sku("SKU-100").fromLocation("RSV-01").toLocation("PICK-01").quantity(60)
                .status(ReplenishmentTaskStatus.OPEN).build());

        ReplenishmentTask confirmed = service.confirm(task.getId());

        assertEquals(ReplenishmentTaskStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(ReplenishmentTaskStatus.CONFIRMED, taskRepository.findById(task.getId()).orElseThrow().getStatus());

        // BR-09/BR-10: the same atomic move as POST /stock/move actually ran, and exactly one StockMove
        // was appended, linked back to this task via relatedTaskId (D14).
        assertEquals(0, inventoryRepository.findBySkuAndLocationCode("SKU-100", "RSV-01").orElseThrow().getQuantity());
        assertEquals(60, inventoryRepository.findBySkuAndLocationCode("SKU-100", "PICK-01").orElseThrow().getQuantity());
        List<StockMove> moves = stockMoveRepository.query(null, null, task.getId());
        assertEquals(1, moves.size());
        assertEquals("SKU-100", moves.get(0).getSku());
        assertEquals("RSV-01", moves.get(0).getFromLocation());
        assertEquals("PICK-01", moves.get(0).getToLocation());
        assertEquals(60, moves.get(0).getQuantity());
        assertEquals(task.getId(), moves.get(0).getRelatedTaskId());
    }

    @Test
    void confirm_ShouldThrowConflictException_AndLeaveTaskOpen_AndInsertNoStockMove_WhenSourceStockBecomesInsufficientBeforeConfirm() {
        // D6: the reserve source's stock changed (drained) between task creation and confirmation.
        givenReserveLocation("RSV-05");
        givenPickingLocation("PICK-05");
        givenStock("SKU-500", "RSV-05", 60);
        ReplenishmentTask task = taskRepository.save(ReplenishmentTask.builder()
                .sku("SKU-500").fromLocation("RSV-05").toLocation("PICK-05").quantity(60)
                .status(ReplenishmentTaskStatus.OPEN).build());
        givenStock("SKU-500", "RSV-05", 10);

        assertThrows(ConflictException.class, () -> service.confirm(task.getId()));

        ReplenishmentTask stillOpen = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(ReplenishmentTaskStatus.OPEN, stillOpen.getStatus());
        assertTrue(stockMoveRepository.query(null, null, task.getId()).isEmpty());
        // No partial debit either (BR-06) — the failed moveStock call never touched inventory.
        assertEquals(10, inventoryRepository.findBySkuAndLocationCode("SKU-500", "RSV-05").orElseThrow().getQuantity());
    }

    @Test
    void confirm_ShouldThrowConflictException_WhenTaskIsAlreadyConfirmed() {
        ReplenishmentTask task = taskRepository.save(ReplenishmentTask.builder()
                .sku("SKU-600").fromLocation("RSV-06").toLocation("PICK-06").quantity(10)
                .status(ReplenishmentTaskStatus.CONFIRMED).build());

        assertThrows(ConflictException.class, () -> service.confirm(task.getId()));
        assertTrue(stockMoveRepository.query(null, null, task.getId()).isEmpty());
    }

    @Test
    void confirm_ShouldThrowConflictException_WhenTaskIsAlreadyCancelled() {
        ReplenishmentTask task = taskRepository.save(ReplenishmentTask.builder()
                .sku("SKU-700").fromLocation("RSV-07").toLocation("PICK-07").quantity(10)
                .status(ReplenishmentTaskStatus.CANCELLED).build());

        assertThrows(ConflictException.class, () -> service.confirm(task.getId()));
        assertTrue(stockMoveRepository.query(null, null, task.getId()).isEmpty());
    }

    // -------------------------------------------------------------------------------------------
    // cancel(id) — FR-TSK-04, BR-08
    // -------------------------------------------------------------------------------------------

    @Test
    void cancel_ShouldThrowNotFoundException_WhenTaskDoesNotExist() {
        assertThrows(NotFoundException.class, () -> service.cancel("missing-id"));
    }

    @Test
    void cancel_ShouldTransitionTaskToCancelled_AndMoveNoStock_WhenSuccessful() {
        givenReserveLocation("RSV-02");
        givenPickingLocation("PICK-02");
        givenStock("SKU-200", "RSV-02", 40);
        ReplenishmentTask task = taskRepository.save(ReplenishmentTask.builder()
                .sku("SKU-200").fromLocation("RSV-02").toLocation("PICK-02").quantity(40)
                .status(ReplenishmentTaskStatus.OPEN).build());

        ReplenishmentTask cancelled = service.cancel(task.getId());

        assertEquals(ReplenishmentTaskStatus.CANCELLED, cancelled.getStatus());
        assertEquals(ReplenishmentTaskStatus.CANCELLED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
        // No stock movement at all: neither location's quantity changed, no StockMove inserted.
        assertEquals(40, inventoryRepository.findBySkuAndLocationCode("SKU-200", "RSV-02").orElseThrow().getQuantity());
        assertTrue(inventoryRepository.findBySkuAndLocationCode("SKU-200", "PICK-02").isEmpty());
        assertTrue(stockMoveRepository.query(null, null, task.getId()).isEmpty());
    }

    @Test
    void cancel_ShouldThrowConflictException_WhenTaskIsAlreadyConfirmed() {
        ReplenishmentTask task = taskRepository.save(ReplenishmentTask.builder()
                .sku("SKU-800").fromLocation("RSV-08").toLocation("PICK-08").quantity(10)
                .status(ReplenishmentTaskStatus.CONFIRMED).build());

        assertThrows(ConflictException.class, () -> service.cancel(task.getId()));
        assertEquals(ReplenishmentTaskStatus.CONFIRMED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancel_ShouldThrowConflictException_WhenTaskIsAlreadyCancelled() {
        ReplenishmentTask task = taskRepository.save(ReplenishmentTask.builder()
                .sku("SKU-900").fromLocation("RSV-09").toLocation("PICK-09").quantity(10)
                .status(ReplenishmentTaskStatus.CANCELLED).build());

        assertThrows(ConflictException.class, () -> service.cancel(task.getId()));
    }

    // -------------------------------------------------------------------------------------------
    // Full evaluate -> confirm sequence, using the seeded-scenario numbers (docs/SRS.md §7 Nota)
    // -------------------------------------------------------------------------------------------

    @Test
    void evaluateThenConfirm_ShouldFullyReplenishPickingLocation_AcrossBothGeneratedTasks() {
        givenPickingLocation("PICK-01");
        givenReserveLocation("RSV-01");
        givenReserveLocation("RSV-02");
        givenRule("SKU-100", "PICK-01", 20, 100);
        givenStock("SKU-100", "PICK-01", 5);
        givenStock("SKU-100", "RSV-01", 60);
        givenStock("SKU-100", "RSV-02", 50);

        ReplenishmentEvaluationResult result = service.evaluate("SKU-100", "PICK-01");
        assertEquals(2, result.getTasks().size());

        for (ReplenishmentTask task : result.getTasks()) {
            ReplenishmentTask confirmed = service.confirm(task.getId());
            assertEquals(ReplenishmentTaskStatus.CONFIRMED, confirmed.getStatus());
        }

        // PICK-01 started at 5, +60 (RSV-01) +35 (RSV-02, capped by the remaining need) = 100.
        assertEquals(100, inventoryRepository.findBySkuAndLocationCode("SKU-100", "PICK-01").orElseThrow().getQuantity());
        assertEquals(0, inventoryRepository.findBySkuAndLocationCode("SKU-100", "RSV-01").orElseThrow().getQuantity());
        assertEquals(15, inventoryRepository.findBySkuAndLocationCode("SKU-100", "RSV-02").orElseThrow().getQuantity());
    }

    /**
     * Hand-written in-memory fake — no Mockito, per the task's TDD instructions. save() upserts by id
     * (matching the real InMemoryReplenishmentTaskRepository's Map.put semantics) rather than blindly
     * appending, so findById reflects a task's latest state after confirm/cancel re-saves it.
     */
    private static class FakeReplenishmentTaskRepository implements ReplenishmentTaskRepository {
        private final List<ReplenishmentTask> tasks = new ArrayList<>();

        @Override
        public ReplenishmentTask save(ReplenishmentTask task) {
            tasks.removeIf(existing -> existing.getId().equals(task.getId()));
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

        @Override
        public Optional<ReplenishmentTask> findById(String id) {
            return tasks.stream().filter(task -> task.getId().equals(id)).findFirst();
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

    /**
     * Hand-written in-memory fake — no Mockito, per the task's TDD instructions. Append-only, like the
     * real InMemoryStockMoveRepository (docs/SRS.md BR-11): no update/delete method exists on the port.
     */
    private static class FakeStockMoveRepository implements StockMoveRepository {
        private final List<StockMove> moves = new ArrayList<>();

        @Override
        public StockMove save(StockMove move) {
            moves.add(move);
            return move;
        }

        @Override
        public List<StockMove> query(String sku, String location, String relatedTaskId) {
            return moves.stream()
                    .filter(move -> sku == null || move.getSku().equals(sku))
                    .filter(move -> location == null
                            || move.getFromLocation().equals(location)
                            || move.getToLocation().equals(location))
                    .filter(move -> relatedTaskId == null || relatedTaskId.equals(move.getRelatedTaskId()))
                    .toList();
        }
    }
}
