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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureWebMvc
@Import(TestConfig.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReplenishmentRuleControllerIntegrationTest {

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
    void createReplenishmentRule_ShouldReturnCreatedRule_WhenLocationIsPicking() throws Exception {
        createLocation("PICK-01", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","locationCode":"PICK-01","min":20,"max":100}
                """;

        mockMvc.perform(post("/replenishment-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku", is("SKU-100")))
                .andExpect(jsonPath("$.locationCode", is("PICK-01")))
                .andExpect(jsonPath("$.min", is(20)))
                .andExpect(jsonPath("$.max", is(100)));
    }

    @Test
    void createReplenishmentRule_ShouldReturnNotFound_WhenLocationDoesNotExist() throws Exception {
        String requestBody = """
                {"sku":"SKU-100","locationCode":"PICK-99","min":20,"max":100}
                """;

        mockMvc.perform(post("/replenishment-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void createReplenishmentRule_ShouldReturnBadRequest_WhenLocationIsReserve() throws Exception {
        createLocation("RSV-01", "RESERVE");

        String requestBody = """
                {"sku":"SKU-100","locationCode":"RSV-01","min":20,"max":100}
                """;

        mockMvc.perform(post("/replenishment-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void createReplenishmentRule_ShouldReturnBadRequest_WhenMinIsGreaterThanMax() throws Exception {
        createLocation("PICK-01", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","locationCode":"PICK-01","min":100,"max":20}
                """;

        mockMvc.perform(post("/replenishment-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void createReplenishmentRule_ShouldReturnConflict_WhenRuleAlreadyExists() throws Exception {
        createLocation("PICK-01", "PICKING");

        String requestBody = """
                {"sku":"SKU-100","locationCode":"PICK-01","min":20,"max":100}
                """;

        mockMvc.perform(post("/replenishment-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/replenishment-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }
}
