package io.tenoro.app.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Domain-level result of a replenishment evaluation (docs/SRS.md §3.5, FR-TSK-01) — not a persisted
 * entity, so it carries no id and no validating constructor, unlike the aggregates above.
 */
@Data
@Builder
public class ReplenishmentEvaluationResult {
    private final boolean replenishmentNeeded;
    private final boolean fullyReplenished;
    @Builder.Default
    private final List<ReplenishmentTask> tasks = List.of();
}
