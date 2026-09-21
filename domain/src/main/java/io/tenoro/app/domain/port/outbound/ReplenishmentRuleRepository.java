package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.ReplenishmentRule;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for ReplenishmentRule persistence operations.
 *
 * ReplenishmentRule has no surrogate id (docs/SRS.md D9) — it is identified by the natural composite key
 * (sku, locationCode), same pattern as InventoryRepository.
 */
public interface ReplenishmentRuleRepository {

    /**
     * Checks whether a rule already exists for the given (sku, locationCode) pair.
     *
     * @param sku          the SKU to look up
     * @param locationCode the location code to look up
     * @return true if a rule exists for this exact pair, false otherwise
     */
    boolean existsBySkuAndLocationCode(String sku, String locationCode);

    /**
     * Retrieves the rule for the given (sku, locationCode) pair, if any. Added for FR-TSK-01 (docs/SRS.md
     * §3.5 step C), which needs the rule's min/max thresholds, not just whether one exists.
     *
     * @param sku          the SKU to look up
     * @param locationCode the location code to look up
     * @return the matching rule, or empty if no rule exists for this exact pair
     */
    Optional<ReplenishmentRule> findBySkuAndLocationCode(String sku, String locationCode);

    /**
     * Saves a replenishment rule to the repository.
     *
     * @param rule the rule to save
     * @return the saved rule
     */
    ReplenishmentRule save(ReplenishmentRule rule);

    /**
     * Retrieves every replenishment rule in the repository.
     *
     * @return a list of all replenishment rules
     */
    List<ReplenishmentRule> findAll();
}
