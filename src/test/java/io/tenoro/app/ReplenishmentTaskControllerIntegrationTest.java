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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration test for FR-TSK-01/02 (POST/GET /replenishment/tasks). No servlet context path in
 * these tests (see CLAUDE.md). The WarehouseSeeder (docs/ARCHITECTURE.md AD-08) loads unconditionally on
 * every context boot, so most tests here use its own dataset directly (docs/SRS.md §7) rather than
 * fixtures that would collide with it; a few tests need a fresh fixture (idempotency, zero-reserve, 404
 * for a missing rule) since the seed data can't exercise those paths.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureWebMvc
@Import(TestConfig.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReplenishmentTaskControllerIntegrationTest {

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

    private void createRule(String sku, String locationCode, int min, int max) throws Exception {
        String requestBody = """
                {"sku":"%s","locationCode":"%s","min":%d,"max":%d}
                """.formatted(sku, locationCode, min, max);

        mockMvc.perform(post("/replenishment-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    private void loadStock(String sku, String locationCode, int quantity) throws Exception {
        String requestBody = """
                {"sku":"%s","locationCode":"%s","quantity":%d}
                """.formatted(sku, locationCode, quantity);

        mockMvc.perform(post("/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions evaluate(String sku, String locationCode) throws Exception {
        String requestBody = """
                {"sku":"%s","locationCode":"%s"}
                """.formatted(sku, locationCode);

        return mockMvc.perform(post("/replenishment/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }

    // -------------------------------------------------------------------------------------------
    // Against the real seeded dataset (docs/SRS.md §7)
    // -------------------------------------------------------------------------------------------

    @Test
    void evaluateReplenishment_ShouldReturnNoTask_WhenSeededStockAlreadyMeetsMin() throws Exception {
        // Seeded: SKU-200/PICK-01 stock=40, rule min=10/max=50 — 40 >= 10, so D5 ("no task needed")
        // triggers before the reserve-lookup step is ever reached. (Note: docs/SRS.md §7's "Nota"
        // paragraph frames SKU-200 as the "no RESERVE stock at all" example, D3's zero-reserve branch —
        // but with these actual seeded numbers, evaluate() never gets past step 4/D5 to reach that
        // branch, since 40 already satisfies min=10. Both are true statements about the seed data; only
        // one of them is what a real evaluate() call on SKU-200 actually observes.)
        evaluate("SKU-200", "PICK-01")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.replenishmentNeeded", is(false)))
                .andExpect(jsonPath("$.fullyReplenished", is(false)))
                .andExpect(jsonPath("$.tasks", empty()));
    }

    @Test
    void evaluateReplenishment_ShouldCreateTwoTasksInDescendingOrder_WhenSeededReserveFullyCoversTheNeed() throws Exception {
        // Seeded: SKU-100/PICK-01 stock=5, rule min=20/max=100 -> needed=95; RSV-01=60, RSV-02=50 (D2:
        // greedy descending -> 60 from RSV-01, then 35 from RSV-02), per docs/SRS.md §7's Nota.
        evaluate("SKU-100", "PICK-01")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.replenishmentNeeded", is(true)))
                .andExpect(jsonPath("$.fullyReplenished", is(true)))
                .andExpect(jsonPath("$.tasks.length()", is(2)))
                .andExpect(jsonPath("$.tasks[0].fromLocation", is("RSV-01")))
                .andExpect(jsonPath("$.tasks[0].toLocation", is("PICK-01")))
                .andExpect(jsonPath("$.tasks[0].quantity", is(60)))
                .andExpect(jsonPath("$.tasks[0].status", is("OPEN")))
                .andExpect(jsonPath("$.tasks[1].fromLocation", is("RSV-02")))
                .andExpect(jsonPath("$.tasks[1].quantity", is(35)));
    }

    @Test
    void evaluateReplenishment_ShouldCreateOnePartialTask_WhenSeededReserveIsInsufficient() throws Exception {
        // Seeded: SKU-300/PICK-02 stock=10, rule min=30/max=120 -> needed=110; RSV-03=70 only (D3:
        // partial replenishment, not rejected), per docs/SRS.md §7's Nota.
        evaluate("SKU-300", "PICK-02")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replenishmentNeeded", is(true)))
                .andExpect(jsonPath("$.fullyReplenished", is(false)))
                .andExpect(jsonPath("$.tasks.length()", is(1)))
                .andExpect(jsonPath("$.tasks[0].fromLocation", is("RSV-03")))
                .andExpect(jsonPath("$.tasks[0].toLocation", is("PICK-02")))
                .andExpect(jsonPath("$.tasks[0].quantity", is(70)));
    }

    // -------------------------------------------------------------------------------------------
    // Validation paths — fresh fixtures, not colliding with the seed
    // -------------------------------------------------------------------------------------------

    @Test
    void evaluateReplenishment_ShouldReturnNotFound_WhenLocationDoesNotExist() throws Exception {
        evaluate("SKU-910", "PICK-99")
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void evaluateReplenishment_ShouldReturnBadRequest_WhenLocationIsReserve() throws Exception {
        createLocation("RSV-91", "RESERVE");

        evaluate("SKU-910", "RSV-91")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void evaluateReplenishment_ShouldReturnNotFound_WhenNoRuleExistsForSkuAndLocation() throws Exception {
        createLocation("PICK-91", "PICKING");

        evaluate("SKU-910", "PICK-91")
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void evaluateReplenishment_ShouldCreateNoTask_WhenNoReserveStockExistsAtAll() throws Exception {
        createLocation("PICK-92", "PICKING");
        createRule("SKU-911", "PICK-92", 10, 50);
        loadStock("SKU-911", "PICK-92", 2);

        evaluate("SKU-911", "PICK-92")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replenishmentNeeded", is(true)))
                .andExpect(jsonPath("$.fullyReplenished", is(false)))
                .andExpect(jsonPath("$.tasks", empty()));
    }

    @Test
    void evaluateReplenishment_ShouldReturnExistingOpenTasks_WithoutCreatingDuplicates_WhenCalledTwice() throws Exception {
        createLocation("PICK-93", "PICKING");
        createLocation("RSV-93", "RESERVE");
        createRule("SKU-912", "PICK-93", 20, 100);
        loadStock("SKU-912", "PICK-93", 5);
        loadStock("SKU-912", "RSV-93", 95);

        String firstTaskId = evaluate("SKU-912", "PICK-93")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()", is(1)))
                .andExpect(jsonPath("$.tasks[0].status", is("OPEN")))
                .andReturn().getResponse().getContentAsString();

        evaluate("SKU-912", "PICK-93")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replenishmentNeeded", is(true)))
                .andExpect(jsonPath("$.fullyReplenished", is(true)))
                .andExpect(jsonPath("$.tasks.length()", is(1)))
                .andExpect(jsonPath("$.tasks[0].id", is(objectMapper.readTree(firstTaskId).at("/tasks/0/id").asText())));

        // Confirms no duplicate was created: the task list has exactly one entry for this pair.
        mockMvc.perform(get("/replenishment/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));
    }

    // -------------------------------------------------------------------------------------------
    // GET /replenishment/tasks
    // -------------------------------------------------------------------------------------------

    @Test
    void getAllReplenishmentTasks_ShouldReturnEmptyList_WhenNoneHaveBeenGenerated() throws Exception {
        mockMvc.perform(get("/replenishment/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void getAllReplenishmentTasks_ShouldReturnEveryCreatedTask() throws Exception {
        // Seeded scenario creates exactly two tasks (see above).
        evaluate("SKU-100", "PICK-01").andExpect(status().isOk());

        mockMvc.perform(get("/replenishment/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[*].fromLocation", containsInAnyOrder("RSV-01", "RSV-02")))
                .andExpect(jsonPath("$[*].status", contains("OPEN", "OPEN")));
    }
}
