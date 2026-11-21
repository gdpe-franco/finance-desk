package io.github.gdpe_franco.internaltransactions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiDocumentationIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void openApiAndSwaggerUiDescribeThePublicApi() throws Exception {
        var openApi = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(openApi.path("info").path("title").asText())
                .isEqualTo("Internal Transactions System API");
        var paths = openApi.path("paths");
        assertThat(paths.has("/api/v1/accounts")).isTrue();
        assertThat(paths.has("/api/v1/accounts/{id}/ledger")).isTrue();
        assertThat(paths.has("/api/v1/transfers")).isTrue();
        var ledgerResponse = paths.path("/api/v1/accounts/{id}/ledger").path("get")
                .path("responses").path("200").path("content").path("*/*").path("schema");
        assertThat(ledgerResponse.path("$ref").asText()).endsWith("/LedgerPageResponse");
        var schemas = openApi.path("components").path("schemas");
        assertThat(schemas.path("LedgerPageResponse").path("properties").path("page").path("$ref").asText())
                .endsWith("/PageMetadata");
        var pageMetadata = schemas.path("PageMetadata").path("properties");
        assertThat(pageMetadata.has("number")).isTrue();
        assertThat(pageMetadata.has("size")).isTrue();
        assertThat(pageMetadata.has("totalElements")).isTrue();
        assertThat(pageMetadata.has("totalPages")).isTrue();
        assertThat(paths.path("/api/v1/accounts").path("post").path("responses").path("400")
                .path("description").asText()).contains("errors");

        var parameters = paths.path("/api/v1/transfers").path("post").path("parameters");
        JsonNode idempotencyKey = StreamSupport.stream(parameters.spliterator(), false)
                .filter(parameter -> parameter.path("name").asText().equals("Idempotency-Key"))
                .findFirst().orElse(null);
        assertThat(idempotencyKey).isNotNull();
        assertThat(idempotencyKey.path("in").asText()).isEqualTo("header");
        assertThat(idempotencyKey.path("required").asBoolean()).isTrue();
        assertThat(paths.path("/api/v1/transfers").path("post").path("responses").has("422")).isTrue();
        assertThat(paths.path("/api/v1/transfers").path("post").path("responses").has("503")).isTrue();

        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
