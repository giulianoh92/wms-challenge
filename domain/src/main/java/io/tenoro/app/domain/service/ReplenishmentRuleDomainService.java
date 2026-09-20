package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.port.inbound.ReplenishmentRuleService;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;

/**
 * Domain service implementation for ReplenishmentRule management.
 *
 * Unlike StockDomainService (docs/ARCHITECTURE.md AD-02), this is a single-entity resource with no
 * cross-entity atomic mutation to protect, so no locking is needed here.
 */
public class ReplenishmentRuleDomainService implements ReplenishmentRuleService {

    private final ReplenishmentRuleRepository replenishmentRuleRepository;
    private final LocationRepository locationRepository;

    public ReplenishmentRuleDomainService(ReplenishmentRuleRepository replenishmentRuleRepository,
                                           LocationRepository locationRepository) {
        this.replenishmentRuleRepository = replenishmentRuleRepository;
        this.locationRepository = locationRepository;
    }

    @Override
    public ReplenishmentRule create(ReplenishmentRule rule) {
        // Business rule: locationCode must reference an existing Location (SPECS.md endpoint #5 rule 1,
        // docs/SRS.md FR-RUL-01) — existence is checked before type, per D7 (missing location is 404,
        // wrong-but-existing type is 400).
        Location location = locationRepository.findByCode(rule.getLocationCode())
                .orElseThrow(() -> new NotFoundException(
                        "Location with code '" + rule.getLocationCode() + "' does not exist"));

        // Business rule: the referenced location must be PICKING (SPECS.md endpoint #5 rule 1, docs/SRS.md
        // D7/BR-02) — a wrong-but-existing type is 400, distinct from a missing location (404).
        if (location.getType() != LocationType.PICKING) {
            throw new IllegalArgumentException("Location with code '" + rule.getLocationCode()
                    + "' must be of type PICKING, but is " + location.getType());
        }

        // 0 <= min <= max (SPECS.md endpoint #5 rule 2, BR-03) is already enforced by ReplenishmentRule's
        // own validating constructor — not duplicated here.

        // Business rule: at most one rule per (sku, locationCode) (SPECS.md endpoint #5 rule 3, BR-04).
        if (replenishmentRuleRepository.existsBySkuAndLocationCode(rule.getSku(), rule.getLocationCode())) {
            throw new ConflictException("A replenishment rule already exists for SKU '" + rule.getSku()
                    + "' at location '" + rule.getLocationCode() + "'");
        }

        return replenishmentRuleRepository.save(rule);
    }
}
