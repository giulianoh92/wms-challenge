package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.model.ReplenishmentEvaluationResult;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskStatus;
import io.tenoro.app.domain.port.inbound.ReplenishmentTaskService;
import io.tenoro.app.domain.port.inbound.StockService;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentTaskRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Domain service implementation for ReplenishmentTask evaluation/generation, listing and lifecycle
 * transitions (docs/SRS.md §3.5, §3.6, FR-TSK-01..04).
 *
 * confirm(id) composes StockService.moveStock rather than duplicating its atomicity logic
 * (docs/ARCHITECTURE.md AD-03) — the only reason this service depends on StockService at all.
 */
public class ReplenishmentTaskDomainService implements ReplenishmentTaskService {

    private final ReplenishmentTaskRepository replenishmentTaskRepository;
    private final ReplenishmentRuleRepository replenishmentRuleRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final StockService stockService;

    public ReplenishmentTaskDomainService(ReplenishmentTaskRepository replenishmentTaskRepository,
                                           ReplenishmentRuleRepository replenishmentRuleRepository,
                                           InventoryRepository inventoryRepository,
                                           LocationRepository locationRepository,
                                           StockService stockService) {
        this.replenishmentTaskRepository = replenishmentTaskRepository;
        this.replenishmentRuleRepository = replenishmentRuleRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.stockService = stockService;
    }

    @Override
    public ReplenishmentEvaluationResult evaluate(String sku, String locationCode) {
        // Step 1 (docs/SRS.md §3.5 B): the location must exist and be PICKING — missing is 404, an
        // existing-but-wrong type is 400 (D7), distinct errors.
        Location location = locationRepository.findByCode(locationCode)
                .orElseThrow(() -> new NotFoundException("Location with code '" + locationCode + "' does not exist"));
        if (location.getType() != LocationType.PICKING) {
            throw new IllegalArgumentException("Location with code '" + locationCode
                    + "' must be of type PICKING, but is " + location.getType());
        }

        // Step 2 (§3.5 C): a rule must exist for (sku, locationCode).
        ReplenishmentRule rule = replenishmentRuleRepository.findBySkuAndLocationCode(sku, locationCode)
                .orElseThrow(() -> new NotFoundException("No replenishment rule found for SKU '" + sku
                        + "' at location '" + locationCode + "'"));

        // Step 3 (§3.5 D): missing InventoryItem record reads as zero stock.
        int stockActual = inventoryRepository.findBySkuAndLocationCode(sku, locationCode)
                .map(InventoryItem::getQuantity)
                .orElse(0);

        // Step 4/F (§3.5, D5): stock already at or above min is the successful, common outcome — not an
        // error, no task created.
        if (stockActual >= rule.getMin()) {
            return ReplenishmentEvaluationResult.builder()
                    .replenishmentNeeded(false)
                    .fullyReplenished(false)
                    .tasks(List.of())
                    .build();
        }

        int needed = rule.getMax() - stockActual;

        // Step 5/H (§3.5, D4): idempotency — an OPEN task already covering this exact (sku, toLocation)
        // pair, from any source, is returned as-is instead of creating a duplicate.
        List<ReplenishmentTask> existingOpenTasks = replenishmentTaskRepository
                .findBySkuAndToLocationAndStatus(sku, locationCode, ReplenishmentTaskStatus.OPEN);
        if (!existingOpenTasks.isEmpty()) {
            int alreadyCovered = existingOpenTasks.stream().mapToInt(ReplenishmentTask::getQuantity).sum();
            return ReplenishmentEvaluationResult.builder()
                    .replenishmentNeeded(true)
                    .fullyReplenished(alreadyCovered >= needed)
                    .tasks(existingOpenTasks)
                    .build();
        }

        // Step 6/K (§3.5, D2): reserve-source selection, isolated in its own method (docs/ARCHITECTURE.md
        // AD-04, NFR-06).
        List<ReserveSource> sources = selectReserveSources(sku, needed);

        // Step 7/L (§3.5, D3): reserve stock totally absent is a valid business outcome, not an error.
        if (sources.isEmpty()) {
            return ReplenishmentEvaluationResult.builder()
                    .replenishmentNeeded(true)
                    .fullyReplenished(false)
                    .tasks(List.of())
                    .build();
        }

        // Step 8/N-O (§3.5, D1): one OPEN task per reserve source actually used, never merged.
        List<ReplenishmentTask> createdTasks = new ArrayList<>();
        int remaining = needed;
        for (ReserveSource source : sources) {
            ReplenishmentTask task = replenishmentTaskRepository.save(ReplenishmentTask.builder()
                    .sku(sku)
                    .fromLocation(source.locationCode())
                    .toLocation(locationCode)
                    .quantity(source.quantityToTake())
                    .status(ReplenishmentTaskStatus.OPEN)
                    .build());
            createdTasks.add(task);
            remaining -= source.quantityToTake();
        }

        return ReplenishmentEvaluationResult.builder()
                .replenishmentNeeded(true)
                .fullyReplenished(remaining == 0)
                .tasks(createdTasks)
                .build();
    }

    @Override
    public List<ReplenishmentTask> findAll() {
        return replenishmentTaskRepository.findAll();
    }

    @Override
    public ReplenishmentTask confirm(String id) {
        ReplenishmentTask task = replenishmentTaskRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ReplenishmentTask with id '" + id + "' does not exist"));

        // task.confirmed() is pure (no I/O) and checked BEFORE the stock move (docs/ARCHITECTURE.md
        // AD-03, §3.6): a non-OPEN task fails fast with 409 (BR-08) without ever attempting a move.
        ReplenishmentTask confirmedTask = task.confirmed();

        // BR-09: reuses StockService.moveStock rather than duplicating AD-02's atomic debit/credit
        // logic. relatedTaskId links the resulting StockMove back to this task (BR-10, D14). If this
        // throws (D6 — insufficient stock at the source), it propagates unchanged and nothing below is
        // reached, so the task is never saved and remains OPEN in the repository.
        stockService.moveStock(task.getSku(), task.getFromLocation(), task.getToLocation(), task.getQuantity(), task.getId());

        return replenishmentTaskRepository.save(confirmedTask);
    }

    @Override
    public ReplenishmentTask cancel(String id) {
        ReplenishmentTask task = replenishmentTaskRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ReplenishmentTask with id '" + id + "' does not exist"));

        // No stock movement (FR-TSK-04) — task.cancelled() throws ConflictException if not OPEN (BR-08).
        return replenishmentTaskRepository.save(task.cancelled());
    }

    /**
     * Reserve-source selection isolated in a single private method (docs/ARCHITECTURE.md AD-04, NFR-06):
     * greedy by descending available quantity (D2), so a different strategy (e.g. FEFO, once lot data
     * exists) can replace this one method without touching the rest of the evaluation flow.
     */
    private List<ReserveSource> selectReserveSources(String sku, int needed) {
        List<InventoryItem> reserveStock = inventoryRepository.findBySku(sku).stream()
                .filter(item -> item.getQuantity() > 0)
                .filter(item -> locationRepository.findByCode(item.getLocationCode())
                        .map(loc -> loc.getType() == LocationType.RESERVE)
                        .orElse(false))
                .sorted(Comparator.comparingInt(InventoryItem::getQuantity).reversed())
                .toList();

        List<ReserveSource> sources = new ArrayList<>();
        int remaining = needed;
        for (InventoryItem item : reserveStock) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, item.getQuantity());
            sources.add(new ReserveSource(item.getLocationCode(), take));
            remaining -= take;
        }
        return sources;
    }

    /**
     * Small local pair, not a domain model: the ordered outcome of selectReserveSources, consumed only
     * within this service.
     */
    private record ReserveSource(String locationCode, int quantityToTake) {
    }
}
