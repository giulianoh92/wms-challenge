package io.tenoro.app.api.dto.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(
    name = "StockMoveResponse",
    title = "Stock Move Response",
    description = "Response object representing an immutable, append-only stock movement record",
    requiredProperties = {"id", "sku", "fromLocation", "toLocation", "quantity", "timestamp"}
)
public class StockMoveResponse {

    @Schema(
        name = "id",
        title = "Id",
        description = "Unique identifier of the stock move",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
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
        description = "Code of the source location",
        example = "RSV-01"
    )
    private String fromLocation;

    @Schema(
        name = "toLocation",
        title = "To Location",
        description = "Code of the destination location",
        example = "PICK-01"
    )
    private String toLocation;

    @Schema(
        name = "quantity",
        title = "Quantity",
        description = "Quantity moved",
        example = "20"
    )
    private int quantity;

    @Schema(
        name = "relatedTaskId",
        title = "Related Task Id",
        description = "Id of the ReplenishmentTask that generated this move via confirmation, or null for a direct move (POST /stock/move)",
        example = "null",
        nullable = true
    )
    private String relatedTaskId;

    @Schema(
        name = "timestamp",
        title = "Timestamp",
        description = "Instant (UTC) at which the move was recorded",
        example = "2024-01-01T12:00:00Z"
    )
    private Instant timestamp;
}
