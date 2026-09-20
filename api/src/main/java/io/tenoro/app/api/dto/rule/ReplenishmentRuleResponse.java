package io.tenoro.app.api.dto.rule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "ReplenishmentRuleResponse",
    title = "Replenishment Rule Response",
    description = "Response object containing a replenishment rule's thresholds",
    requiredProperties = {"sku", "locationCode", "min", "max"}
)
public class ReplenishmentRuleResponse {

    @Schema(
        name = "sku",
        title = "SKU",
        description = "Stock keeping unit identifier",
        example = "SKU-100"
    )
    private String sku;

    @Schema(
        name = "locationCode",
        title = "Location Code",
        description = "Code of the picking location this rule applies to",
        example = "PICK-01"
    )
    private String locationCode;

    @Schema(
        name = "min",
        title = "Minimum",
        description = "Minimum quantity threshold that triggers replenishment when stock falls below it",
        example = "20"
    )
    private int min;

    @Schema(
        name = "max",
        title = "Maximum",
        description = "Maximum quantity to replenish up to",
        example = "100"
    )
    private int max;
}
