package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ConflictException;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * System-generated id (docs/SRS.md D9 does NOT apply here, unlike ReplenishmentRule/InventoryItem — this
 * aggregate has a real surrogate id), same generation pattern as StockMove.
 *
 * The fromLocation/toLocation type invariants (RESERVE/PICKING, BR-07) are NOT validated in this
 * constructor: this model has no repository access to resolve a code to its Location.type, so that
 * cross-entity check lives in ReplenishmentTaskDomainService, which already knows both types from the
 * evaluation flow (docs/SRS.md §3.5). This constructor only validates the same null/blank/positive
 * invariants every other model in this repo already validates (see StockMove for the closest precedent).
 */
@Data
@Builder
public class ReplenishmentTask {
    private final String id;
    private final String sku;
    private final String fromLocation;
    private final String toLocation;
    private final int quantity;
    private final ReplenishmentTaskStatus status;

    public ReplenishmentTask(String id, String sku, String fromLocation, String toLocation, int quantity,
                              ReplenishmentTaskStatus status) {
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
        this.id = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id;
        this.sku = sku;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.quantity = quantity;
        // Defaults to OPEN when constructed via the normal evaluation flow (docs/SRS.md §3.3/§3.4).
        this.status = (status == null) ? ReplenishmentTaskStatus.OPEN : status;
    }

    /**
     * Transitions this task to CONFIRMED (docs/SRS.md §3.4, FR-TSK-03), per this repo's "mutation
     * returns a new instance" convention (never setters) — see CLAUDE.md. Pure and side-effect-free:
     * ReplenishmentTaskDomainService calls this BEFORE attempting the underlying stock move
     * (docs/ARCHITECTURE.md AD-03), so an already-terminal task fails fast with 409 before any I/O.
     *
     * @return a new CONFIRMED instance with all other fields unchanged
     * @throws ConflictException if this task is not currently OPEN (BR-08 — terminal states never
     *         transition again, and none of their fields ever change)
     */
    public ReplenishmentTask confirmed() {
        if (status != ReplenishmentTaskStatus.OPEN) {
            throw new ConflictException("ReplenishmentTask with id '" + id + "' cannot be confirmed: current status is " + status + ", expected OPEN");
        }
        return new ReplenishmentTask(id, sku, fromLocation, toLocation, quantity, ReplenishmentTaskStatus.CONFIRMED);
    }

    /**
     * Transitions this task to CANCELLED (docs/SRS.md §3.4, FR-TSK-04), per this repo's "mutation
     * returns a new instance" convention (never setters) — see CLAUDE.md.
     *
     * @return a new CANCELLED instance with all other fields unchanged
     * @throws ConflictException if this task is not currently OPEN (BR-08 — terminal states never
     *         transition again, and none of their fields ever change)
     */
    public ReplenishmentTask cancelled() {
        if (status != ReplenishmentTaskStatus.OPEN) {
            throw new ConflictException("ReplenishmentTask with id '" + id + "' cannot be cancelled: current status is " + status + ", expected OPEN");
        }
        return new ReplenishmentTask(id, sku, fromLocation, toLocation, quantity, ReplenishmentTaskStatus.CANCELLED);
    }
}
