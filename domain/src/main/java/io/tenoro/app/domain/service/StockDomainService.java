package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.port.inbound.StockService;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;

import java.util.List;

/**
 * Domain service implementation for stock (InventoryItem) management.
 */
public class StockDomainService implements StockService {

    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;

    public StockDomainService(InventoryRepository inventoryRepository, LocationRepository locationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
    }

    @Override
    public InventoryItem loadStock(InventoryItem item) {
        // Business rule: locationCode must reference an existing Location, PICKING or RESERVE alike
        // (SPECS.md endpoint #3 rule 1, docs/SRS.md FR-STK-01).
        if (!locationRepository.existsByCode(item.getLocationCode())) {
            throw new NotFoundException("Location with code '" + item.getLocationCode() + "' does not exist");
        }

        return inventoryRepository.upsert(item);
    }

    @Override
    public List<InventoryItem> query(String sku, String location) {
        if (sku != null && location != null) {
            return inventoryRepository.findBySku(sku).stream()
                    .filter(item -> item.getLocationCode().equals(location))
                    .toList();
        }
        if (sku != null) {
            return inventoryRepository.findBySku(sku);
        }
        if (location != null) {
            return inventoryRepository.findByLocationCode(location);
        }
        return inventoryRepository.findAll();
    }
}
