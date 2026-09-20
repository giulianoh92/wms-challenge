package io.tenoro.app.infra.adapter.inbound.web.mappers;

import io.tenoro.app.api.dto.stock.InventoryItemResponse;
import io.tenoro.app.api.dto.stock.LoadStockRequest;
import io.tenoro.app.api.dto.stock.StockMoveResponse;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.StockMove;

public class StockResponseMapper {

    public static InventoryItemResponse fromDomain(InventoryItem item) {
        return InventoryItemResponse.builder()
                .sku(item.getSku())
                .locationCode(item.getLocationCode())
                .quantity(item.getQuantity())
                .build();
    }

    /**
     * Quantity >= 0 validation happens here, at the DTO/mapping boundary, via InventoryItem's own
     * validating constructor (SPECS.md endpoint #3 rule 2): a negative quantity throws
     * IllegalArgumentException, which the scoped ReplenishmentExceptionHandler maps to 400.
     */
    public static InventoryItem toDomain(LoadStockRequest request) {
        return InventoryItem.builder()
                .sku(request.getSku())
                .locationCode(request.getLocationCode())
                .quantity(request.getQuantity())
                .build();
    }

    public static StockMoveResponse fromDomain(StockMove move) {
        return StockMoveResponse.builder()
                .id(move.getId())
                .sku(move.getSku())
                .fromLocation(move.getFromLocation())
                .toLocation(move.getToLocation())
                .quantity(move.getQuantity())
                .relatedTaskId(move.getRelatedTaskId())
                .timestamp(move.getTimestamp())
                .build();
    }
}
