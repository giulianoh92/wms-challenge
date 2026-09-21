package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.ReplenishmentEvaluationResult;
import io.tenoro.app.domain.model.ReplenishmentTask;

import java.util.List;

/**
 * Inbound port (Use Case) for ReplenishmentTask evaluation/generation, listing and lifecycle
 * transitions (docs/SRS.md FR-TSK-01..04).
 */
public interface ReplenishmentTaskService {

    /**
     * Evaluates a SKU's stock at a picking location against its replenishment rule and generates 0..n
     * OPEN replenishment tasks from reserve locations (docs/SRS.md §3.5).
     *
     * @param sku          the SKU to evaluate
     * @param locationCode the picking location code to evaluate
     * @return the evaluation result: whether replenishment was needed, whether it was fully covered, and
     *         the resulting (or pre-existing OPEN, per D4) tasks
     * @throws io.tenoro.app.domain.exception.NotFoundException if the location does not exist, or if no
     *         replenishment rule exists for the (sku, locationCode) pair
     * @throws IllegalArgumentException if the referenced location is not of type PICKING (docs/SRS.md D7)
     */
    ReplenishmentEvaluationResult evaluate(String sku, String locationCode);

    /**
     * Retrieves every replenishment task, regardless of status.
     *
     * @return a list of all replenishment tasks
     */
    List<ReplenishmentTask> findAll();

    /**
     * Confirms an OPEN replenishment task (docs/SRS.md FR-TSK-03, §3.6): transitions it to CONFIRMED
     * and executes the underlying stock move (BR-09) from fromLocation to toLocation, for quantity,
     * with this task's id as the move's relatedTaskId (BR-10).
     *
     * @param id the id of the task to confirm
     * @return the confirmed task
     * @throws io.tenoro.app.domain.exception.NotFoundException if no task exists with this id
     * @throws io.tenoro.app.domain.exception.ConflictException if the task is not currently OPEN
     *         (BR-08), or if the underlying stock move fails due to insufficient stock at the source
     *         (D6) — in that case the task remains OPEN
     */
    ReplenishmentTask confirm(String id);

    /**
     * Cancels an OPEN replenishment task (docs/SRS.md FR-TSK-04): transitions it to CANCELLED. No
     * stock move is performed.
     *
     * @param id the id of the task to cancel
     * @return the cancelled task
     * @throws io.tenoro.app.domain.exception.NotFoundException if no task exists with this id
     * @throws io.tenoro.app.domain.exception.ConflictException if the task is not currently OPEN (BR-08)
     */
    ReplenishmentTask cancel(String id);
}
