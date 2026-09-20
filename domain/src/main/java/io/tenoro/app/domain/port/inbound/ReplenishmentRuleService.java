package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.ReplenishmentRule;

/**
 * Inbound port (Use Case) for ReplenishmentRule management operations.
 *
 * No findAll/list method: SPECS.md endpoint #5 defines only POST /replenishment-rules — there is no list
 * endpoint for this resource. A later task (evaluate/generate replenishment tasks) reads rules internally
 * through ReplenishmentRuleRepository, not through this inbound port.
 */
public interface ReplenishmentRuleService {

    /**
     * Creates a new replenishment rule.
     *
     * @param rule the rule to create
     * @return the created rule
     * @throws io.tenoro.app.domain.exception.NotFoundException if the referenced location does not exist
     * @throws IllegalArgumentException if the referenced location is not of type PICKING (docs/SRS.md D7),
     *         or if 0 &lt;= min &lt;= max is violated
     * @throws io.tenoro.app.domain.exception.ConflictException if a rule already exists for the same
     *         (sku, locationCode) pair
     */
    ReplenishmentRule create(ReplenishmentRule rule);
}
