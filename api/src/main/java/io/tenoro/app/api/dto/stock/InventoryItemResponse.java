package io.tenoro.app.api.dto.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "InventoryItemResponse",
    title = "Inventory Item Response",
    description = "Response object containing stock information for a SKU at a location",
    requiredProperties = {"sku", "locationCode", "quantity"}
)
public class InventoryItemResponse {

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
        description = "Code of the location holding this stock",
        example = "PICK-01"
    )
    private String locationCode;

    @Schema(
        name = "quantity",
        title = "Quantity",
        description = "Current quantity for this SKU at this location",
        example = "5"
    )
    private int quantity;
}
