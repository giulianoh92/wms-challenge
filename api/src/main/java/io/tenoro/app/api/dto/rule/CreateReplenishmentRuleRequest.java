package io.tenoro.app.api.dto.rule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "CreateReplenishmentRuleRequest",
    title = "Create Replenishment Rule Request",
    description = "Request object for defining the replenishment thresholds of a SKU at a picking location",
    requiredProperties = {"sku", "locationCode", "min", "max"}
)
public class CreateReplenishmentRuleRequest {

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
        description = "Code of the picking location this rule applies to",
        example = "PICK-01",
        minLength = 1,
        type = "string"
    )
    private final String locationCode;

    @Schema(
        name = "min",
        title = "Minimum",
        description = "Minimum quantity threshold that triggers replenishment when stock falls below it",
        example = "20",
        minimum = "0",
        type = "integer"
    )
    private final int min;

    @Schema(
        name = "max",
        title = "Maximum",
        description = "Maximum quantity to replenish up to",
        example = "100",
        minimum = "0",
        type = "integer"
    )
    private final int max;
}
