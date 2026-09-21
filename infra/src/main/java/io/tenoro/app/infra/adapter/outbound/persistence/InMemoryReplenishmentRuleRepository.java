package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flat ConcurrentHashMap keyed "sku|locationCode", same pattern as InMemoryInventoryRepository
 * (docs/ARCHITECTURE.md AD-07).
 */
@Repository
public class InMemoryReplenishmentRuleRepository implements ReplenishmentRuleRepository {

    private final Map<String, ReplenishmentRule> rules = new ConcurrentHashMap<>();

    @Override
    public boolean existsBySkuAndLocationCode(String sku, String locationCode) {
        return rules.containsKey(key(sku, locationCode));
    }

    @Override
    public ReplenishmentRule save(ReplenishmentRule rule) {
        rules.put(key(rule.getSku(), rule.getLocationCode()), rule);
        return rule;
    }

    @Override
    public List<ReplenishmentRule> findAll() {
        return new ArrayList<>(rules.values());
    }

    @Override
    public Optional<ReplenishmentRule> findBySkuAndLocationCode(String sku, String locationCode) {
        return Optional.ofNullable(rules.get(key(sku, locationCode)));
    }

    private static String key(String sku, String locationCode) {
        return sku + "|" + locationCode;
    }
}
