package io.tenoro.app.api.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "ReplenishmentTaskResponse",
    title = "Replenishment Task Response",
    description = "Response object representing a single replenishment task",
    requiredProperties = {"id", "sku", "fromLocation", "toLocation", "quantity", "status"}
)
public class ReplenishmentTaskResponse {

    @Schema(
        name = "id",
        title = "Id",
        description = "System-generated task identifier",
        example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private String id;

    @Schema(
        name = "sku",
        title = "SKU",
        description = "Stock keeping unit identifier",
        example = "SKU-100"
    )
    private String sku;

    @Schema(
        name = "fromLocation",
        title = "From Location",
        description = "Reserve location this task moves stock from",
        example = "RSV-01"
    )
    private String fromLocation;

    @Schema(
        name = "toLocation",
        title = "To Location",
        description = "Picking location this task moves stock to",
        example = "PICK-01"
    )
    private String toLocation;

    @Schema(
        name = "quantity",
        title = "Quantity",
        description = "Quantity to move once this task is confirmed",
        example = "60"
    )
    private int quantity;

    @Schema(
        name = "status",
        title = "Status",
        description = "Current task status",
        example = "OPEN",
        allowableValues = {"OPEN", "CONFIRMED", "CANCELLED"}
    )
    private String status;
}
