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
        createLocation("PICK-01", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","locationCode":"PICK-01","quantity":5}
                """;

        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku", is("SKU-100")))
                .andExpect(jsonPath("$.locationCode", is("PICK-01")))
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
        createLocation("PICK-01", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","locationCode":"PICK-01","quantity":-1}
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
        createLocation("PICK-01", "PICKING");
        createLocation("PICK-02", "PICKING");

        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"PICK-01","quantity":5}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-200","locationCode":"PICK-02","quantity":3}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/stock").queryParam("sku", "SKU-100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku", is("SKU-100")))
                .andExpect(jsonPath("$[0].locationCode", is("PICK-01")));
    }

    @Test
    void getStock_FilteredByLocation_ShouldReturnOnlyMatchingItems() throws Exception {
        createLocation("PICK-01", "PICKING");
        createLocation("PICK-02", "PICKING");

        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"PICK-01","quantity":5}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-200","locationCode":"PICK-02","quantity":3}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/stock").queryParam("location", "PICK-02")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku", is("SKU-200")))
                .andExpect(jsonPath("$[0].locationCode", is("PICK-02")));
    }

    // ---------------------------------------------------------------------------------------
    // POST /stock/move
    // ---------------------------------------------------------------------------------------

    @Test
    void moveStock_ShouldMoveQuantityBetweenLocations_WhenSourceHasEnoughStock() throws Exception {
        createLocation("RSV-01", "RESERVE");
        createLocation("PICK-01", "PICKING");
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"RSV-01","quantity":20}
                                """))
                .andExpect(status().isOk());

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-01","to":"PICK-01","quantity":15}
                """;

        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.sku", is("SKU-100")))
                .andExpect(jsonPath("$.fromLocation", is("RSV-01")))
                .andExpect(jsonPath("$.toLocation", is("PICK-01")))
                .andExpect(jsonPath("$.quantity", is(15)))
                .andExpect(jsonPath("$.relatedTaskId", nullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        mockMvc.perform(get("/stock").queryParam("location", "RSV-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].quantity", is(5)));
        mockMvc.perform(get("/stock").queryParam("location", "PICK-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].quantity", is(15)));
    }

    @Test
    void moveStock_ShouldReturnConflict_WhenSourceHasInsufficientStock() throws Exception {
        createLocation("RSV-01", "RESERVE");
        createLocation("PICK-01", "PICKING");
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"RSV-01","quantity":5}
                                """))
                .andExpect(status().isOk());

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-01","to":"PICK-01","quantity":10}
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
        createLocation("PICK-01", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-99","to":"PICK-01","quantity":5}
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
        createLocation("RSV-01", "RESERVE");
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"RSV-01","quantity":5}
                                """))
                .andExpect(status().isOk());

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-01","to":"PICK-99","quantity":5}
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
        createLocation("RSV-01", "RESERVE");
        createLocation("PICK-01", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","from":"RSV-01","to":"PICK-01","quantity":0}
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
        createLocation("RSV-01", "RESERVE");
        createLocation("RSV-02", "RESERVE");
        createLocation("PICK-01", "PICKING");
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","locationCode":"RSV-01","quantity":20}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-200","locationCode":"RSV-02","quantity":20}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-100","from":"RSV-01","to":"PICK-01","quantity":5}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-200","from":"RSV-02","to":"PICK-01","quantity":3}
                                """))
                .andExpect(status().isOk());

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

        mockMvc.perform(get("/stock/moves").queryParam("location", "RSV-02")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku", is("SKU-200")));

        mockMvc.perform(get("/stock/moves").queryParam("location", "PICK-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
