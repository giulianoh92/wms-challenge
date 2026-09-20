package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.InventoryItem;

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
}
