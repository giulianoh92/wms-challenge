package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.StockMove;

import java.util.List;

/**
 * Outbound port for StockMove persistence operations.
 *
 * StockMove is immutable and append-only (docs/SRS.md D13, BR-11) — this port intentionally exposes
 * no update or delete method of any kind, enforcing that invariant architecturally rather than by
 * convention alone.
 */
public interface StockMoveRepository {

    /**
     * Appends a new stock move record. Never overwrites or replaces an existing one.
     *
     * @param move the stock move to store
     * @return the stored stock move
     */
    StockMove save(StockMove move);

    /**
     * Retrieves stock moves, optionally filtered by SKU, location (matching either fromLocation or
     * toLocation) and/or relatedTaskId. Filters are combined as AND; a null filter is not applied.
     * Returned most-recent-first (docs/SRS.md D15).
     *
     * @param sku           the SKU to filter by, or null to not filter by SKU
     * @param location      the location code to filter by (matches fromLocation or toLocation), or null
     * @param relatedTaskId the related task id to filter by, or null to not filter by it
     * @return the matching stock moves, most-recent-first, empty if none
     */
    List<StockMove> query(String sku, String location, String relatedTaskId);
}
