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

    private org.springframework.test.web.servlet.ResultActions confirm(String taskId) throws Exception {
        return mockMvc.perform(post("/replenishment/tasks/" + taskId + "/confirm"));
    }

    private org.springframework.test.web.servlet.ResultActions cancel(String taskId) throws Exception {
        return mockMvc.perform(post("/replenishment/tasks/" + taskId + "/cancel"));
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

    // -------------------------------------------------------------------------------------------
    // POST /replenishment/tasks/{id}/confirm — FR-TSK-03, §3.6
    // -------------------------------------------------------------------------------------------

    @Test
    void confirmReplenishmentTask_ShouldReturnNotFound_WhenTaskIdDoesNotExist() throws Exception {
        confirm("missing-task-id")
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void confirmReplenishmentTask_ShouldReturnConflict_WhenTaskIsNotOpen() throws Exception {
        String taskId = evaluate("SKU-100", "PICK-01")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String firstTaskId = objectMapper.readTree(taskId).at("/tasks/0/id").asText();
        confirm(firstTaskId).andExpect(status().isOk());

        confirm(firstTaskId)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void confirmReplenishmentTask_ShouldTransitionToConfirmed_MoveStock_AndAppearInStockMoves() throws Exception {
        String evaluateResponse = evaluate("SKU-100", "PICK-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()", is(2)))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(evaluateResponse).at("/tasks/0/id").asText();

        confirm(taskId)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(taskId)))
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.fromLocation", is("RSV-01")))
                .andExpect(jsonPath("$.toLocation", is("PICK-01")))
                .andExpect(jsonPath("$.quantity", is(60)));

        // The task list now shows this task as CONFIRMED (BR-08 — persisted, not just returned).
        mockMvc.perform(get("/replenishment/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + taskId + "')].status", contains("CONFIRMED")));

        // BR-09/BR-10: the underlying stock move actually ran and is traceable via GET /stock/moves.
        mockMvc.perform(get("/stock/moves").param("relatedTaskId", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].sku", is("SKU-100")))
                .andExpect(jsonPath("$[0].fromLocation", is("RSV-01")))
                .andExpect(jsonPath("$[0].toLocation", is("PICK-01")))
                .andExpect(jsonPath("$[0].quantity", is(60)))
                .andExpect(jsonPath("$[0].relatedTaskId", is(taskId)));

        // Stock actually moved: PICK-01 gained the 60 units debited from RSV-01.
        mockMvc.perform(get("/stock").param("sku", "SKU-100").param("location", "RSV-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity", is(0)));
    }

    @Test
    void confirmReplenishmentTask_ShouldReturnConflict_AndLeaveTaskOpen_WhenSourceStockBecomesInsufficient() throws Exception {
        createLocation("PICK-94", "PICKING");
        createLocation("RSV-94", "RESERVE");
        createRule("SKU-913", "PICK-94", 20, 100);
        loadStock("SKU-913", "PICK-94", 5);
        loadStock("SKU-913", "RSV-94", 95);

        String evaluateResponse = evaluate("SKU-913", "PICK-94")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(evaluateResponse).at("/tasks/0/id").asText();

        // D6: the reserve source's stock drains after task creation, before confirmation is attempted.
        loadStock("SKU-913", "RSV-94", 10);

        confirm(taskId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));

        mockMvc.perform(get("/replenishment/tasks"))
                .andExpect(jsonPath("$[?(@.id=='" + taskId + "')].status", contains("OPEN")));
        mockMvc.perform(get("/stock/moves").param("relatedTaskId", taskId))
                .andExpect(jsonPath("$", empty()));
    }

    // -------------------------------------------------------------------------------------------
    // POST /replenishment/tasks/{id}/cancel — FR-TSK-04
    // -------------------------------------------------------------------------------------------

    @Test
    void cancelReplenishmentTask_ShouldReturnNotFound_WhenTaskIdDoesNotExist() throws Exception {
        cancel("missing-task-id")
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void cancelReplenishmentTask_ShouldReturnConflict_WhenTaskIsNotOpen() throws Exception {
        String evaluateResponse = evaluate("SKU-100", "PICK-01")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(evaluateResponse).at("/tasks/0/id").asText();
        cancel(taskId).andExpect(status().isOk());

        cancel(taskId)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void cancelReplenishmentTask_ShouldTransitionToCancelled_AndMoveNoStock() throws Exception {
        String evaluateResponse = evaluate("SKU-300", "PICK-02")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()", is(1)))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(evaluateResponse).at("/tasks/0/id").asText();

        cancel(taskId)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(taskId)))
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        mockMvc.perform(get("/replenishment/tasks"))
                .andExpect(jsonPath("$[?(@.id=='" + taskId + "')].status", contains("CANCELLED")));

        // No stock movement at all: no StockMove for this task, and RSV-03's stock is unchanged.
        mockMvc.perform(get("/stock/moves").param("relatedTaskId", taskId))
                .andExpect(jsonPath("$", empty()));
        mockMvc.perform(get("/stock").param("sku", "SKU-300").param("location", "RSV-03"))
                .andExpect(jsonPath("$[0].quantity", is(70)));
    }
}
