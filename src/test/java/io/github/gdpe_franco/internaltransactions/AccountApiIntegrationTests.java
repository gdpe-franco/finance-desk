package io.github.gdpe_franco.internaltransactions;

import io.github.gdpe_franco.internaltransactions.domain.account.ClabeAccountNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @BeforeEach
    void clearAccounts() {
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    void createsAndRetrievesAccountWhileIgnoringCallerControlledAndUnknownFields() throws Exception {
        var created = mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content("""
                                {"ownerName":"  Ada Lovelace  ","currency":"MXN","accountNumber":"123",
                                 "balance":500.00,"status":"CLOSED","extra":"ignored"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.ownerName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        var body = created.getResponse().getContentAsString();
        var accountId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).get("id").asLong();
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

    @Test
    void rejectsInvalidCreateRequestsWithoutPersistingAnAccount() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content("{\"ownerName\":\"Ada\",\"currency\":\"CAD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content("{\"ownerName\":\"   \",\"currency\":\"MXN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content("{\"ownerName\":\"%s\",\"currency\":\"MXN\"}".formatted("A".repeat(121))))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class)).isZero();
    }

    @Test
    void returnsNotFoundForMissingAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Account not found"));
    }

    @Test
    void mysqlConstraintsRejectInvalidBalancesCurrenciesStatusesAndDuplicateNumbers() {
        var negativeBalanceNumber = ClabeAccountNumber.generate();
        assertThatThrownBy(() -> insertAccount(negativeBalanceNumber, "Ada", "MXN", "-0.01", "ACTIVE"))
                .isInstanceOf(DataAccessException.class);

        var invalidCurrencyNumber = ClabeAccountNumber.generate();
        assertThatThrownBy(() -> insertAccount(invalidCurrencyNumber, "Ada", "CAD", "0.00", "ACTIVE"))
                .isInstanceOf(DataAccessException.class);

        var invalidStatusNumber = ClabeAccountNumber.generate();
        assertThatThrownBy(() -> insertAccount(invalidStatusNumber, "Ada", "MXN", "0.00", "INVALID"))
                .isInstanceOf(DataAccessException.class);

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
}
