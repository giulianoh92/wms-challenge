package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flat ConcurrentHashMap keyed "sku|locationCode", no secondary index — GET /stock?location= is a
 * linear scan over .values(), not an indexed lookup (docs/ARCHITECTURE.md AD-07).
 */
@Repository
public class InMemoryInventoryRepository implements InventoryRepository {

    private final Map<String, InventoryItem> items = new ConcurrentHashMap<>();

    @Override
    public InventoryItem upsert(InventoryItem item) {
        items.put(key(item.getSku(), item.getLocationCode()), item);
        return item;
    }

    @Override
    public List<InventoryItem> findBySku(String sku) {
        return items.values().stream()
                .filter(item -> item.getSku().equals(sku))
                .toList();
    }

    @Override
    public List<InventoryItem> findByLocationCode(String locationCode) {
        return items.values().stream()
                .filter(item -> item.getLocationCode().equals(locationCode))
                .toList();
    }

    @Override
    public List<InventoryItem> findAll() {
        return new ArrayList<>(items.values());
    }

    private static String key(String sku, String locationCode) {
        return sku + "|" + locationCode;
    }
}
