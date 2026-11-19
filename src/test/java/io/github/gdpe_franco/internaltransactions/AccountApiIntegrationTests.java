package io.github.gdpe_franco.internaltransactions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gdpe_franco.internaltransactions.domain.account.ClabeAccountNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearAccounts() {
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM transfers");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @ParameterizedTest(name = "creates and retrieves an account in {0}")
    @MethodSource("supportedCurrencies")
    void createsAndRetrievesAccountWhileIgnoringCallerControlledAndUnknownFields(String currency) throws Exception {
        var created = mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content("""
                                {"ownerName":"  Ada Lovelace  ","currency":"%s","accountNumber":"123",
                                 "balance":500.00,"status":"CLOSED","extra":"ignored"}
                                """.formatted(currency)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.ownerName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.currency").value(currency))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        var body = created.getResponse().getContentAsString();
        var accountId = objectMapper.readTree(body).get("id").asLong();
        var accountNumber = jdbcTemplate.queryForObject(
                "SELECT account_number FROM accounts WHERE id = ?", String.class, accountId);

        assertThat(ClabeAccountNumber.isValid(accountNumber)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @ParameterizedTest(name = "rejects invalid account request: {0}")
    @MethodSource("invalidCreateRequests")
    void rejectsInvalidCreateRequestsWithoutPersistingAnAccount(String scenario, String request) throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class)).isZero();
    }

    private static Stream<String> supportedCurrencies() {
        return Stream.of("MXN", "USD", "EUR");
    }

    private static Stream<Arguments> invalidCreateRequests() {
        return Stream.of(
                Arguments.of("unsupported currency", "{\"ownerName\":\"Ada\",\"currency\":\"CAD\"}"),
                Arguments.of("blank owner", "{\"ownerName\":\"   \",\"currency\":\"MXN\"}"),
                Arguments.of("overlong owner", "{\"ownerName\":\"%s\",\"currency\":\"MXN\"}"
                        .formatted("A".repeat(121))));
    }

    @Test
    void returnsNotFoundForMissingAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Account not found"));
    }

    @Test
    void updatesOwnerNameAndIgnoresExtraAccountFields() throws Exception {
        var accountId = createAccount("Original owner");
        var original = objectMapper.readTree(mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andReturn().getResponse().getContentAsString());

        var updated = mockMvc.perform(patch("/api/v1/accounts/{id}", accountId)
                        .contentType("application/json")
                        .content("""
                                {"ownerName":"  New owner  ","balance":999.00,"currency":"EUR","status":"CLOSED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.ownerName").value("New owner"))
                .andExpect(jsonPath("$.accountNumber").value(original.get("accountNumber").asText()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(original.get("version").asLong() + 1))
                .andReturn();

        var updatedBody = objectMapper.readTree(updated.getResponse().getContentAsString());
        assertThat(Instant.parse(updatedBody.get("createdAt").asText()))
                .isEqualTo(Instant.parse(original.get("createdAt").asText()));
        assertThat(Instant.parse(updatedBody.get("updatedAt").asText()))
                .isAfter(Instant.parse(original.get("updatedAt").asText()));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("New owner"));
    }

    @ParameterizedTest(name = "rejects invalid owner-name update: {0}")
    @MethodSource("invalidOwnerNameUpdates")
    void rejectsInvalidOwnerNameUpdatesWithoutChangingAccount(String scenario, String request) throws Exception {
        var accountId = createAccount("Original owner");

        mockMvc.perform(patch("/api/v1/accounts/{id}", accountId)
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Original owner"))
                .andExpect(jsonPath("$.version").value(0));
    }

    private static Stream<Arguments> invalidOwnerNameUpdates() {
        return Stream.of(
                Arguments.of("missing name", "{\"extra\":true}"),
                Arguments.of("null name", "{\"ownerName\":null}"),
                Arguments.of("blank name", "{\"ownerName\":\"   \"}"),
                Arguments.of("overlong name", "{\"ownerName\":\"%s\"}".formatted("A".repeat(121))));
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingAccount() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/999999")
                        .contentType("application/json")
                        .content("{\"ownerName\":\"New owner\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @ParameterizedTest(name = "MySQL rejects invalid account data: {0}")
    @MethodSource("invalidDatabaseAccounts")
    void mysqlConstraintsRejectInvalidAccountData(String scenario, String accountNumber, String currency,
                                                   String balance, String status) {
        assertThatThrownBy(() -> insertAccount(accountNumber, "Ada", currency, balance, status))
                .isInstanceOf(DataAccessException.class);
    }

    private static Stream<Arguments> invalidDatabaseAccounts() {
        return Stream.of(
                Arguments.of("negative balance", ClabeAccountNumber.generate(), "MXN", "-0.01", "ACTIVE"),
                Arguments.of("unsupported currency", ClabeAccountNumber.generate(), "CAD", "0.00", "ACTIVE"),
                Arguments.of("invalid status", ClabeAccountNumber.generate(), "MXN", "0.00", "INVALID"),
                Arguments.of("malformed account number", "not-a-clabe", "MXN", "0.00", "ACTIVE"));
    }

    @Test
    void mysqlConstraintRejectsDuplicateAccountNumbers() {
        var duplicateNumber = ClabeAccountNumber.generate();
        insertAccount(duplicateNumber, "Ada", "MXN", "0.00", "ACTIVE");
        assertThatThrownBy(() -> insertAccount(duplicateNumber, "Grace", "MXN", "0.00", "ACTIVE"))
                .isInstanceOf(DataAccessException.class);
    }

    private void insertAccount(String accountNumber, String ownerName, String currency, String balance, String status) {
        jdbcTemplate.update("""
                        INSERT INTO accounts (account_number, owner_name, currency, balance, status)
                        VALUES (?, ?, ?, ?, ?)
                        """, accountNumber, ownerName, currency, balance, status);
    }

    private long createAccount(String ownerName) throws Exception {
        return createAccount(ownerName, "MXN");
    }

    private long createAccount(String ownerName, String currency) throws Exception {
        var created = mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content("{\"ownerName\":\"%s\",\"currency\":\"%s\"}"
                                .formatted(ownerName, currency)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

}
