package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.ReplenishmentEvaluationResult;
import io.tenoro.app.domain.model.ReplenishmentTask;

import java.util.List;

/**
 * Inbound port (Use Case) for ReplenishmentTask evaluation/generation and listing (docs/SRS.md FR-TSK-01/02).
 *
 * confirm/cancel (FR-TSK-03/04) are a later task (docs/ARCHITECTURE.md AD-03) and are deliberately not
 * part of this port yet.
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
}
