package io.tenoro.app.api.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(
    name = "EvaluateReplenishmentResponse",
    title = "Evaluate Replenishment Response",
    description = "Response object describing the outcome of a replenishment evaluation",
    requiredProperties = {"replenishmentNeeded", "fullyReplenished", "tasks"}
)
public class EvaluateReplenishmentResponse {

    @Schema(
        name = "replenishmentNeeded",
        title = "Replenishment Needed",
        description = "Whether the picking location's stock was below its rule's minimum threshold",
        example = "true"
    )
    private boolean replenishmentNeeded;

    @Schema(
        name = "fullyReplenished",
        title = "Fully Replenished",
        description = "Whether reserve stock was enough to reach the rule's maximum threshold in full",
        example = "true"
    )
    private boolean fullyReplenished;

    @Schema(
        name = "tasks",
        title = "Tasks",
        description = "The OPEN replenishment tasks created by this evaluation, or the already-existing OPEN tasks for this pair (docs/SRS.md D4)"
    )
    private List<ReplenishmentTaskResponse> tasks;
}
