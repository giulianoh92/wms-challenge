package io.tenoro.app.infra.config;

import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.domain.port.inbound.ReplenishmentRuleService;
import io.tenoro.app.domain.port.inbound.StockService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Loads the fixed seed dataset (docs/SRS.md §7) on every application startup, unconditionally (no profile
 * gating), through the existing domain inbound ports rather than the repositories directly — so the seed
 * is validated by the same rules a real request would go through (docs/ARCHITECTURE.md AD-08).
 *
 * <p>Sequenced by referential dependency: locations first (rules and stock reference them by code), then
 * replenishment rules, then initial stock — loaded via {@link StockService#loadStock(InventoryItem)},
 * never {@link StockService#moveStock}, so the seed never produces a {@code StockMove} (docs/SRS.md D14).
 */
@Component
public class WarehouseSeeder implements CommandLineRunner {

    private final LocationService locationService;
    private final ReplenishmentRuleService replenishmentRuleService;
    private final StockService stockService;

    public WarehouseSeeder(LocationService locationService, ReplenishmentRuleService replenishmentRuleService,
                            StockService stockService) {
        this.locationService = locationService;
        this.replenishmentRuleService = replenishmentRuleService;
        this.stockService = stockService;
    }

    @Override
    public void run(String... args) {
        seedLocations();
        seedReplenishmentRules();
        seedInitialStock();
    }

    private void seedLocations() {
        locationService.create(new Location("PICK-01", LocationType.PICKING));
        locationService.create(new Location("PICK-02", LocationType.PICKING));
        locationService.create(new Location("RSV-01", LocationType.RESERVE));
        locationService.create(new Location("RSV-02", LocationType.RESERVE));
        locationService.create(new Location("RSV-03", LocationType.RESERVE));
    }

    private void seedReplenishmentRules() {
        replenishmentRuleService.create(new ReplenishmentRule("SKU-100", "PICK-01", 20, 100));
        replenishmentRuleService.create(new ReplenishmentRule("SKU-200", "PICK-01", 10, 50));
        replenishmentRuleService.create(new ReplenishmentRule("SKU-300", "PICK-02", 30, 120));
    }

    private void seedInitialStock() {
        stockService.loadStock(new InventoryItem("SKU-100", "PICK-01", 5));
        stockService.loadStock(new InventoryItem("SKU-200", "PICK-01", 40));
        stockService.loadStock(new InventoryItem("SKU-300", "PICK-02", 10));
        stockService.loadStock(new InventoryItem("SKU-100", "RSV-01", 60));
        stockService.loadStock(new InventoryItem("SKU-100", "RSV-02", 50));
        stockService.loadStock(new InventoryItem("SKU-300", "RSV-03", 70));
    }
}
