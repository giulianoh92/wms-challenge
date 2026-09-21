package io.tenoro.app.api.dto.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "MoveStockRequest",
    title = "Move Stock Request",
    description = "Request object for moving a quantity of a SKU from one location to another",
    requiredProperties = {"sku", "from", "to", "quantity"}
)
public class MoveStockRequest {

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
        name = "from",
        title = "From Location",
        description = "Code of the source location",
        example = "RSV-01",
        minLength = 1,
        type = "string"
    )
    private final String from;

    @Schema(
        name = "to",
        title = "To Location",
        description = "Code of the destination location",
        example = "PICK-01",
        minLength = 1,
        type = "string"
    )
    private final String to;

    @Schema(
        name = "quantity",
        title = "Quantity",
        description = "Quantity to move; must be strictly greater than zero",
        example = "20",
        minimum = "1",
        type = "integer"
    )
    private final int quantity;
}
