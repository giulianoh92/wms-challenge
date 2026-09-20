package io.tenoro.app.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryItem {
    private final String sku;
    private final String locationCode;
    private final int quantity;

    public InventoryItem(String sku, String locationCode, int quantity) {
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("SKU cannot be null or blank");
        }
        if (locationCode == null || locationCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Location code cannot be null or blank");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        this.sku = sku;
        this.locationCode = locationCode;
        this.quantity = quantity;
    }
}
