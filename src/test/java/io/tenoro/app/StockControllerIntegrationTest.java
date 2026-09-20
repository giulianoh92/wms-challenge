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
}
