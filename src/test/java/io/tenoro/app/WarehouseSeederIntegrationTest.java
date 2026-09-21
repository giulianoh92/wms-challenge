package io.tenoro.app;

import io.tenoro.app.config.TestConfig;
import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.StockMove;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.domain.port.inbound.ReplenishmentRuleService;
import io.tenoro.app.domain.port.inbound.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Asserts on the state left behind by {@link io.tenoro.app.infra.config.WarehouseSeeder} after the
 * application context (and therefore the CommandLineRunner) has booted, per docs/SRS.md §7 and
 * docs/ARCHITECTURE.md AD-08. This is not an HTTP test: it autowires the domain inbound ports directly.
 */
@SpringBootTest(classes = Application.class)
@Import(TestConfig.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WarehouseSeederIntegrationTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private ReplenishmentRuleService replenishmentRuleService;

    @Autowired
    private StockService stockService;

    @Test
    void seed_ShouldCreateExactlyTheFiveExpectedLocations() {
        List<Location> locations = locationService.findAll();

        assertThat(locations).hasSize(5);
        assertThat(locations)
                .extracting(Location::getCode, Location::getType)
                .containsExactlyInAnyOrder(
                        tuple("PICK-01", LocationType.PICKING),
                        tuple("PICK-02", LocationType.PICKING),
                        tuple("RSV-01", LocationType.RESERVE),
                        tuple("RSV-02", LocationType.RESERVE),
                        tuple("RSV-03", LocationType.RESERVE)
                );
    }

    @Test
    void seed_ShouldCreateExactlyTheThreeExpectedReplenishmentRules() {
        // ReplenishmentRuleService intentionally has no list method (T4 scope). Verify indirectly: a
        // duplicate create for the same (sku, locationCode) pair now throws ConflictException, proving
        // the seeded rule already exists with that key.
        assertThatThrownBy(() -> replenishmentRuleService.create(
                new ReplenishmentRule("SKU-100", "PICK-01", 20, 100)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> replenishmentRuleService.create(
                new ReplenishmentRule("SKU-200", "PICK-01", 10, 50)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> replenishmentRuleService.create(
                new ReplenishmentRule("SKU-300", "PICK-02", 30, 120)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void seed_ShouldLoadExactlyTheSixExpectedInventoryItems() {
        List<InventoryItem> items = stockService.query(null, null);

        assertThat(items).hasSize(6);
        assertThat(items)
                .extracting(InventoryItem::getSku, InventoryItem::getLocationCode, InventoryItem::getQuantity)
                .containsExactlyInAnyOrder(
                        tuple("SKU-100", "PICK-01", 5),
                        tuple("SKU-200", "PICK-01", 40),
                        tuple("SKU-300", "PICK-02", 10),
                        tuple("SKU-100", "RSV-01", 60),
                        tuple("SKU-100", "RSV-02", 50),
                        tuple("SKU-300", "RSV-03", 70)
                );
    }

    @Test
    void seed_ShouldNeverProduceAnyStockMove() {
        List<StockMove> moves = stockService.listMoves(null, null, null);

        assertThat(moves).isEmpty();
    }
}
