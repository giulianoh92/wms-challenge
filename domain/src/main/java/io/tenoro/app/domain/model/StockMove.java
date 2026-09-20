package io.tenoro.app.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, append-only record of a stock movement between two locations (docs/SRS.md D13, BR-11),
 * modeled on Odoo 19's {@code stock.move}: no states, inserted exactly once at the instant the stock
 * actually moves. This class exposes no mutator methods of any kind — a correction is a new
 * compensating StockMove in the opposite direction, never an edit of an existing record.
 */
@Data
@Builder
public class StockMove {
    private final String id;
    private final String sku;
    private final String fromLocation;
    private final String toLocation;
    private final int quantity;
    private final String relatedTaskId;
    private final Instant timestamp;

    public StockMove(String id, String sku, String fromLocation, String toLocation, int quantity,
                      String relatedTaskId, Instant timestamp) {
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("SKU cannot be null or blank");
        }
        if (fromLocation == null || fromLocation.trim().isEmpty()) {
            throw new IllegalArgumentException("From location cannot be null or blank");
        }
        if (toLocation == null || toLocation.trim().isEmpty()) {
            throw new IllegalArgumentException("To location cannot be null or blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        this.id = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id;
        this.sku = sku;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.quantity = quantity;
        this.relatedTaskId = relatedTaskId;
        this.timestamp = timestamp;
    }
}
