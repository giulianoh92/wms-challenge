package io.tenoro.app.api.dto.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "LoadStockRequest",
    title = "Load Stock Request",
    description = "Request object for loading (setting) the stock quantity of a SKU at a location",
    requiredProperties = {"sku", "locationCode", "quantity"}
)
public class LoadStockRequest {

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
        description = "Code of the location where stock is being loaded",
        example = "PICK-01",
        minLength = 1,
        type = "string"
    )
    private final String locationCode;

    @Schema(
        name = "quantity",
        title = "Quantity",
        description = "Absolute quantity to set for this SKU at this location (upsert, not additive)",
        example = "5",
        minimum = "0",
        type = "integer"
    )
    private final int quantity;
}
