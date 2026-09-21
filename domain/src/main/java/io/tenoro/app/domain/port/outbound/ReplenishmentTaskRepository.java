package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for ReplenishmentTask persistence operations.
 *
 * Unlike ReplenishmentRule/InventoryItem, ReplenishmentTask has a system-generated id (docs/SRS.md D9
 * does not apply to it), so it is identified by that id rather than a natural composite key.
 */
public interface ReplenishmentTaskRepository {

    /**
     * Saves a replenishment task to the repository.
     *
     * @param task the task to save
     * @return the saved task
     */
    ReplenishmentTask save(ReplenishmentTask task);

    /**
     * Retrieves every replenishment task in the repository, regardless of status.
     *
     * @return a list of all replenishment tasks
     */
    List<ReplenishmentTask> findAll();

    /**
     * Retrieves the tasks matching the given SKU, destination location and status — used for the D4
     * idempotency check (docs/SRS.md §3.5, D4): an already-OPEN task for the exact same (sku, toLocation)
     * pair short-circuits creating a new one.
     *
     * @param sku        the SKU to look up
     * @param toLocation the destination (picking) location code to look up
     * @param status     the status to filter by
     * @return the matching tasks, empty if none
     */
    List<ReplenishmentTask> findBySkuAndToLocationAndStatus(String sku, String toLocation, ReplenishmentTaskStatus status);

    /**
     * Retrieves a single replenishment task by its system-generated id (used by confirm/cancel,
     * docs/SRS.md FR-TSK-03/04).
     *
     * @param id the task id to look up
     * @return the matching task, or empty if none exists with that id
     */
    Optional<ReplenishmentTask> findById(String id);
}
