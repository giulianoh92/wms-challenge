package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.InventoryItem;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for InventoryItem persistence operations.
 *
 * InventoryItem has no surrogate id (docs/SRS.md D8) — it is identified by the natural composite key
 * (sku, locationCode). {@link #upsert(InventoryItem)} therefore has set semantics: it replaces any
 * existing entry for that key rather than accumulating.
 */
public interface InventoryRepository {

    /**
     * Sets the stock entry for its (sku, locationCode) key, replacing any existing entry.
     *
     * @param item the inventory item to store
     * @return the stored inventory item
     */
    InventoryItem upsert(InventoryItem item);

    /**
     * Retrieves every inventory item for the given SKU, across all locations.
     *
     * @param sku the SKU to filter by
     * @return the matching inventory items, empty if none
     */
    List<InventoryItem> findBySku(String sku);

    /**
     * Retrieves every inventory item at the given location, across all SKUs.
     *
     * @param locationCode the location code to filter by
     * @return the matching inventory items, empty if none
     */
    List<InventoryItem> findByLocationCode(String locationCode);

    /**
     * Retrieves the exact inventory item for a single (sku, locationCode) key, if any.
     *
     * @param sku          the SKU to look up
     * @param locationCode the location code to look up
     * @return the matching inventory item, or empty if no record exists for that exact key
     */
    Optional<InventoryItem> findBySkuAndLocationCode(String sku, String locationCode);

    /**
     * Retrieves every inventory item in the repository.
     *
     * @return a list of all inventory items
     */
    List<InventoryItem> findAll();
}
