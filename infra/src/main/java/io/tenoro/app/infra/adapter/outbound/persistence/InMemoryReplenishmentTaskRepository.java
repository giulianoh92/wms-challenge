package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskStatus;
import io.tenoro.app.domain.port.outbound.ReplenishmentTaskRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flat ConcurrentHashMap keyed by id (docs/ARCHITECTURE.md AD-07), same pattern as
 * InMemoryReplenishmentRuleRepository — but keyed by the system-generated id, since ReplenishmentTask
 * (unlike ReplenishmentRule) has a real surrogate id.
 */
@Repository
public class InMemoryReplenishmentTaskRepository implements ReplenishmentTaskRepository {

    private final Map<String, ReplenishmentTask> tasks = new ConcurrentHashMap<>();

    @Override
    public ReplenishmentTask save(ReplenishmentTask task) {
        tasks.put(task.getId(), task);
        return task;
    }

    @Override
    public List<ReplenishmentTask> findAll() {
        return new ArrayList<>(tasks.values());
    }

    @Override
    public List<ReplenishmentTask> findBySkuAndToLocationAndStatus(String sku, String toLocation, ReplenishmentTaskStatus status) {
        return tasks.values().stream()
                .filter(task -> task.getSku().equals(sku)
                        && task.getToLocation().equals(toLocation)
                        && task.getStatus() == status)
                .toList();
    }

    @Override
    public Optional<ReplenishmentTask> findById(String id) {
        return Optional.ofNullable(tasks.get(id));
    }
}
