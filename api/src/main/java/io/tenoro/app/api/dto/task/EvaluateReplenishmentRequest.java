package io.tenoro.app.api.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "EvaluateReplenishmentRequest",
    title = "Evaluate Replenishment Request",
    description = "Request object for evaluating whether a SKU at a picking location needs replenishment",
    requiredProperties = {"sku", "locationCode"}
)
public class EvaluateReplenishmentRequest {

    @Schema(
        name = "sku",
        title = "SKU",
        description = "Stock keeping unit identifier",
        example = "SKU-100",
        minLength = 1,
        type = "string"
    )
    private final String sku;

    @Schema(
        name = "locationCode",
        title = "Location Code",
        description = "Code of the picking location to evaluate",
        example = "PICK-01",
        minLength = 1,
        type = "string"
    )
    private final String locationCode;
}
