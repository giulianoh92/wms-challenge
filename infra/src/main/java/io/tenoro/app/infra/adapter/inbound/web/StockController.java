package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tenoro.app.api.dto.ErrorResponse;
import io.tenoro.app.api.dto.stock.InventoryItemResponse;
import io.tenoro.app.api.dto.stock.LoadStockRequest;
import io.tenoro.app.api.dto.stock.MoveStockRequest;
import io.tenoro.app.api.dto.stock.StockMoveResponse;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.StockMove;
import io.tenoro.app.domain.port.inbound.StockService;
import io.tenoro.app.infra.adapter.inbound.web.mappers.StockResponseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * No local try/catch: exceptions propagate to the scoped ReplenishmentExceptionHandler
 * (infra/config/ReplenishmentExceptionHandler.java), per docs/ARCHITECTURE.md AD-05.
 */
@RestController
@RequestMapping("/stock")
@Tag(name = "Stock", description = "Inventory stock management endpoints")
public class StockController {

    @Autowired
    private StockService stockService;

    @Operation(summary = "Load stock", description = "Sets (upserts) the available quantity of a SKU at a location")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock loaded successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = InventoryItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data (e.g. negative quantity)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "The referenced location does not exist",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<InventoryItemResponse> loadStock(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Stock load details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = LoadStockRequest.class)
            )
        )
        @RequestBody LoadStockRequest request
    ) {
        InventoryItem item = StockResponseMapper.toDomain(request);
        InventoryItem loaded = stockService.loadStock(item);
        return ResponseEntity.ok(StockResponseMapper.fromDomain(loaded));
    }

    @Operation(summary = "Query stock", description = "Retrieves stock, optionally filtered by SKU and/or location (combined as AND)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = InventoryItemResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<InventoryItemResponse>> getStock(
        @Parameter(description = "Filter by SKU")
        @RequestParam(required = false) String sku,
        @Parameter(description = "Filter by location code")
        @RequestParam(required = false) String location
    ) {
        List<InventoryItemResponse> responses = stockService.query(sku, location).stream()
                .map(StockResponseMapper::fromDomain)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Move stock", description = "Moves a quantity of a SKU from one location to another, atomically, recording a StockMove (relatedTaskId is null for a direct move)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock moved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = StockMoveResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data (e.g. quantity <= 0, from equals to, blank sku/location)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "The source or destination location does not exist",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Insufficient stock at the source location",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/move")
    public ResponseEntity<StockMoveResponse> moveStock(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Stock move details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MoveStockRequest.class)
            )
        )
        @RequestBody MoveStockRequest request
    ) {
        StockMove move = stockService.moveStock(request.getSku(), request.getFrom(), request.getTo(),
                request.getQuantity(), null);
        return ResponseEntity.ok(StockResponseMapper.fromDomain(move));
    }

    @Operation(summary = "Query stock move history", description = "Retrieves the StockMove history, most-recent-first, optionally filtered by sku, location (matching fromLocation or toLocation) and relatedTaskId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock move history retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = StockMoveResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/moves")
    public ResponseEntity<List<StockMoveResponse>> getStockMoves(
        @Parameter(description = "Filter by SKU")
        @RequestParam(required = false) String sku,
        @Parameter(description = "Filter by location (matches fromLocation or toLocation)")
        @RequestParam(required = false) String location,
        @Parameter(description = "Filter by related task id")
        @RequestParam(required = false) String relatedTaskId
    ) {
        List<StockMoveResponse> responses = stockService.listMoves(sku, location, relatedTaskId).stream()
                .map(StockResponseMapper::fromDomain)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
