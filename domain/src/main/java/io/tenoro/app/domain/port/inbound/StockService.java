package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.StockMove;

import java.util.List;

/**
 * Inbound port (Use Case) for stock (InventoryItem) management operations.
 */
public interface StockService {

    /**
     * Sets (upserts) the quantity of a SKU at a location.
     *
     * @param item the inventory item to load
     * @return the stored inventory item
     * @throws io.tenoro.app.domain.exception.NotFoundException if the referenced location does not exist
     */
    InventoryItem loadStock(InventoryItem item);

    /**
     * Retrieves inventory items, optionally filtered by SKU and/or location. When both filters are
     * given they are combined as AND (docs/SRS.md FR-STK-02). A null filter is not applied.
     *
     * @param sku      the SKU to filter by, or null to not filter by SKU
     * @param location the location code to filter by, or null to not filter by location
     * @return the matching inventory items, empty if none
     */
    List<InventoryItem> query(String sku, String location);

    /**
     * Moves a quantity of a SKU from one location to another, atomically (docs/SRS.md BR-06): the
     * source is debited, the destination is credited, and a StockMove is appended, all in the same
     * atomic effect, or none of it happens at all.
     *
     * @param sku           the SKU to move
     * @param fromLocation  the source location code
     * @param toLocation    the destination location code
     * @param quantity      the quantity to move; must be strictly greater than zero
     * @param relatedTaskId the id of the ReplenishmentTask this move is confirming, or null for a
     *                      direct move (i.e. a call from POST /stock/move)
     * @return the appended stock move record
     * @throws IllegalArgumentException                          if quantity &lt;= 0, or fromLocation
     *                                                            equals toLocation
     * @throws io.tenoro.app.domain.exception.NotFoundException  if either location does not exist
     * @throws io.tenoro.app.domain.exception.ConflictException  if the source location has
     *                                                            insufficient stock
     */
    StockMove moveStock(String sku, String fromLocation, String toLocation, int quantity, String relatedTaskId);

    /**
     * Retrieves the stock move history, optionally filtered by SKU, location (matching either
     * fromLocation or toLocation) and/or relatedTaskId. Filters are combined as AND; a null filter is
     * not applied. Returned most-recent-first (docs/SRS.md D15).
     *
     * @param sku           the SKU to filter by, or null to not filter by SKU
     * @param location      the location code to filter by (matches fromLocation or toLocation), or null
     * @param relatedTaskId the related task id to filter by, or null to not filter by it
     * @return the matching stock moves, most-recent-first, empty if none
     */
    List<StockMove> listMoves(String sku, String location, String relatedTaskId);
}
