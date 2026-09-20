package io.tenoro.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tenoro.app.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureWebMvc
@Import(TestConfig.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StockControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
    }

    private void createLocation(String code, String type) throws Exception {
        String requestBody = """
                {"code":"%s","type":"%s"}
                """.formatted(code, type);

        mockMvc.perform(post("/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void loadStock_ShouldReturnLoadedStock_WhenLocationExists() throws Exception {
        // A location code not present in the WarehouseSeeder dataset (docs/SRS.md §7), which is loaded
        // unconditionally on every context boot (docs/ARCHITECTURE.md AD-08) — reusing PICK-01 here
        // would fail at location creation with a 409, before this test's stock logic even runs.
        createLocation("PICK-94", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","locationCode":"PICK-94","quantity":5}
                """;

        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku", is("SKU-100")))
                .andExpect(jsonPath("$.locationCode", is("PICK-94")))
                .andExpect(jsonPath("$.quantity", is(5)));
    }

    @Test
    void loadStock_ShouldReturnNotFound_WhenLocationDoesNotExist() throws Exception {
        String requestBody = """
                {"sku":"SKU-100","locationCode":"PICK-99","quantity":5}
                """;

        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void loadStock_ShouldReturnBadRequest_WhenQuantityIsNegative() throws Exception {
        createLocation("PICK-94", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","locationCode":"PICK-94","quantity":-1}
                """;

        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void getStock_FilteredBySku_ShouldReturnOnlyMatchingItems() throws Exception {
        // Both the location codes and the SKU must avoid the WarehouseSeeder dataset (docs/SRS.md §7):
        // it seeds SKU-100 at three locations, so filtering by "SKU-100" here would also match those
        // seeded rows and break the hasSize(1) assertion below (docs/ARCHITECTURE.md AD-08).
        createLocation("PICK-94", "PICKING");
        createLocation("PICK-95", "PICKING");

        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-910","locationCode":"PICK-94","quantity":5}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-920","locationCode":"PICK-95","quantity":3}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/stock").queryParam("sku", "SKU-910")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku", is("SKU-910")))
                .andExpect(jsonPath("$[0].locationCode", is("PICK-94")));
    }

    @Test
    void getStock_FilteredByLocation_ShouldReturnOnlyMatchingItems() throws Exception {
        createLocation("PICK-94", "PICKING");
        createLocation("PICK-95", "PICKING");

        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"PICK-94","quantity":5}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-200","locationCode":"PICK-95","quantity":3}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/stock").queryParam("location", "PICK-95")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku", is("SKU-200")))
                .andExpect(jsonPath("$[0].locationCode", is("PICK-95")));
    }

    // ---------------------------------------------------------------------------------------
    // POST /stock/move
    // ---------------------------------------------------------------------------------------

    @Test
    void moveStock_ShouldMoveQuantityBetweenLocations_WhenSourceHasEnoughStock() throws Exception {
        createLocation("RSV-94", "RESERVE");
        createLocation("PICK-94", "PICKING");
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"RSV-94","quantity":20}
                                """))
                .andExpect(status().isOk());

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-94","to":"PICK-94","quantity":15}
                """;

        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.sku", is("SKU-100")))
                .andExpect(jsonPath("$.fromLocation", is("RSV-94")))
                .andExpect(jsonPath("$.toLocation", is("PICK-94")))
                .andExpect(jsonPath("$.quantity", is(15)))
                .andExpect(jsonPath("$.relatedTaskId", nullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        mockMvc.perform(get("/stock").queryParam("location", "RSV-94")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].quantity", is(5)));
        mockMvc.perform(get("/stock").queryParam("location", "PICK-94")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].quantity", is(15)));
    }

    @Test
    void moveStock_ShouldReturnConflict_WhenSourceHasInsufficientStock() throws Exception {
        createLocation("RSV-94", "RESERVE");
        createLocation("PICK-94", "PICKING");
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"RSV-94","quantity":5}
                                """))
                .andExpect(status().isOk());

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-94","to":"PICK-94","quantity":10}
                """;

        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void moveStock_ShouldReturnNotFound_WhenSourceLocationDoesNotExist() throws Exception {
        createLocation("PICK-94", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-99","to":"PICK-94","quantity":5}
                """;

        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void moveStock_ShouldReturnNotFound_WhenDestinationLocationDoesNotExist() throws Exception {
        createLocation("RSV-94", "RESERVE");
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"RSV-94","quantity":5}
                                """))
                .andExpect(status().isOk());

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-94","to":"PICK-99","quantity":5}
                """;

        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void moveStock_ShouldReturnBadRequest_WhenQuantityIsZeroOrNegative() throws Exception {
        createLocation("RSV-94", "RESERVE");
        createLocation("PICK-94", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-94","to":"PICK-94","quantity":0}
                """;

        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    // ---------------------------------------------------------------------------------------
    // GET /stock/moves
    // ---------------------------------------------------------------------------------------

    @Test
    void getStockMoves_ShouldReturnMostRecentFirst_AndRespectFilters() throws Exception {
        createLocation("RSV-94", "RESERVE");
        createLocation("RSV-95", "RESERVE");
        createLocation("PICK-94", "PICKING");
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"RSV-94","quantity":20}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-200","locationCode":"RSV-95","quantity":20}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","from":"RSV-94","to":"PICK-94","quantity":5}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-200","from":"RSV-95","to":"PICK-94","quantity":3}
                                """))
                .andExpect(status().isOk());

        // The seed never produces a StockMove (docs/SRS.md D14), so /stock/moves starts empty after
        // every context boot regardless of the WarehouseSeeder — hasSize(2) below reflects only the
        // two moves this test performs.
        mockMvc.perform(get("/stock/moves")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sku", is("SKU-200")))
                .andExpect(jsonPath("$[1].sku", is("SKU-100")));

        mockMvc.perform(get("/stock/moves").queryParam("sku", "SKU-100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku", is("SKU-100")));

        mockMvc.perform(get("/stock/moves").queryParam("location", "RSV-95")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku", is("SKU-200")));

        mockMvc.perform(get("/stock/moves").queryParam("location", "PICK-94")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
