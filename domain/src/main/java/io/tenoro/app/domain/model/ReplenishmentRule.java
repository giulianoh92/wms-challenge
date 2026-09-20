package io.tenoro.app.domain.model;

import lombok.Builder;
import lombok.Data;

/**
 * No surrogate id (docs/SRS.md D9) — identified by the natural composite key (sku, locationCode), same
 * reasoning as InventoryItem (D8): the uniqueness rule (BR-04) already implies this is the natural key.
 */
@Data
@Builder
public class ReplenishmentRule {
    private final String sku;
    private final String locationCode;
    private final int min;
    private final int max;

    public ReplenishmentRule(String sku, String locationCode, int min, int max) {
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("SKU cannot be null or blank");
        }
        if (locationCode == null || locationCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Location code cannot be null or blank");
        }
        if (min < 0) {
            throw new IllegalArgumentException("Min cannot be negative");
        }
        if (min > max) {
            throw new IllegalArgumentException("Min cannot be greater than max");
        }
        this.sku = sku;
        this.locationCode = locationCode;
        this.min = min;
        this.max = max;
    }
}
