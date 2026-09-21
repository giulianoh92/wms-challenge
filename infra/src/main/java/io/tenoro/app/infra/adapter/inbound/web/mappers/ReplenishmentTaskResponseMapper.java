package io.tenoro.app.infra.adapter.inbound.web.mappers;

import io.tenoro.app.api.dto.task.EvaluateReplenishmentResponse;
import io.tenoro.app.api.dto.task.ReplenishmentTaskResponse;
import io.tenoro.app.domain.model.ReplenishmentEvaluationResult;
import io.tenoro.app.domain.model.ReplenishmentTask;

public class ReplenishmentTaskResponseMapper {

    public static ReplenishmentTaskResponse fromDomain(ReplenishmentTask task) {
        return ReplenishmentTaskResponse.builder()
                .id(task.getId())
                .sku(task.getSku())
                .fromLocation(task.getFromLocation())
                .toLocation(task.getToLocation())
                .quantity(task.getQuantity())
                .status(task.getStatus().name())
                .build();
    }

    public static EvaluateReplenishmentResponse fromDomain(ReplenishmentEvaluationResult result) {
        return EvaluateReplenishmentResponse.builder()
                .replenishmentNeeded(result.isReplenishmentNeeded())
                .fullyReplenished(result.isFullyReplenished())
                .tasks(result.getTasks().stream().map(ReplenishmentTaskResponseMapper::fromDomain).toList())
                .build();
    }
}
