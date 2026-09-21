package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.StockMove;
import io.tenoro.app.domain.port.inbound.StockService;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.StockMoveRepository;

import java.time.Instant;
import java.util.List;

/**
 * Domain service implementation for stock (InventoryItem) management.
 *
 * docs/ARCHITECTURE.md AD-02: a single monitor ({@code stockLock}) wraps every InventoryItem
 * mutation — both {@link #loadStock(InventoryItem)} (FR-STK-01) and
 * {@link #moveStock(String, String, String, int, String)} (FR-STK-03) — because a
 * ConcurrentHashMap only guarantees atomicity per individual key, not across the multi-step
 * debit/credit/append sequence a move performs. Without sharing this lock, a concurrent loadStock
 * on one of a move's two locations could interleave mid-move and silently overwrite a
 * just-debited value (a lost update).
 */
public class StockDomainService implements StockService {

    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final StockMoveRepository stockMoveRepository;
    private final Object stockLock = new Object();

    public StockDomainService(InventoryRepository inventoryRepository, LocationRepository locationRepository,
                               StockMoveRepository stockMoveRepository) {
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.stockMoveRepository = stockMoveRepository;
    }

    @Override
    public InventoryItem loadStock(InventoryItem item) {
        // Entire body under stockLock (docs/ARCHITECTURE.md AD-02): loadStock shares the same monitor
        // as moveStock so a concurrent load can never interleave with a move's debit/credit pair.
        synchronized (stockLock) {
            // Business rule: locationCode must reference an existing Location, PICKING or RESERVE alike
            // (SPECS.md endpoint #3 rule 1, docs/SRS.md FR-STK-01).
            if (!locationRepository.existsByCode(item.getLocationCode())) {
                throw new NotFoundException("Location with code '" + item.getLocationCode() + "' does not exist");
            }

            return inventoryRepository.upsert(item);
        }
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

    @Override
    public StockMove moveStock(String sku, String fromLocation, String toLocation, int quantity, String relatedTaskId) {
        // Basic request-shape validation (SPECS.md endpoint #6, docs/SRS.md FR-STK-03) — pure, no I/O,
        // so it runs before either repository is touched.
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
        if (fromLocation.equals(toLocation)) {
            throw new IllegalArgumentException("Source and destination locations must be different");
        }

        // Existence checks are read-only and not part of the debit/credit race (docs/ARCHITECTURE.md
        // AD-02 "Alcance de la garantia"), so they run outside the lock.
        if (!locationRepository.existsByCode(fromLocation)) {
            throw new NotFoundException("Location with code '" + fromLocation + "' does not exist");
        }
        if (!locationRepository.existsByCode(toLocation)) {
            throw new NotFoundException("Location with code '" + toLocation + "' does not exist");
        }

        synchronized (stockLock) {
            int available = inventoryRepository.findBySkuAndLocationCode(sku, fromLocation)
                    .map(InventoryItem::getQuantity)
                    .orElse(0);
            if (quantity > available) {
                // No write happens on this path (BR-06): validation fails before any debit/credit.
                throw new ConflictException("Insufficient stock for SKU '" + sku + "' at location '"
                        + fromLocation + "': requested " + quantity + ", available " + available);
            }

            inventoryRepository.upsert(InventoryItem.builder()
                    .sku(sku)
                    .locationCode(fromLocation)
                    .quantity(available - quantity)
                    .build());

            int destinationAvailable = inventoryRepository.findBySkuAndLocationCode(sku, toLocation)
                    .map(InventoryItem::getQuantity)
                    .orElse(0);
            inventoryRepository.upsert(InventoryItem.builder()
                    .sku(sku)
                    .locationCode(toLocation)
                    .quantity(destinationAvailable + quantity)
                    .build());

            StockMove move = StockMove.builder()
                    .sku(sku)
                    .fromLocation(fromLocation)
                    .toLocation(toLocation)
                    .quantity(quantity)
                    .relatedTaskId(relatedTaskId)
                    .timestamp(Instant.now())
                    .build();
            return stockMoveRepository.save(move);
        }
    }

    @Override
    public List<StockMove> listMoves(String sku, String location, String relatedTaskId) {
        return stockMoveRepository.query(sku, location, relatedTaskId);
    }
}
