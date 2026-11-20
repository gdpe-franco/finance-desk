package io.github.gdpe_franco.internaltransactions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TransferConcurrencyIntegrationTests {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("transactions_concurrency_test")
            .withUsername("transactions_test")
            .withPassword("transactions_test")
            .withCommand("--innodb-lock-wait-timeout=2");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM transfers");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    void competingTransfersCannotOverspendOneSourceAccount() throws Exception {
        var sourceId = createFundedAccount("Source", "1000.00");
        var firstDestinationId = createFundedAccount("First destination", "0.00");
        var secondDestinationId = createFundedAccount("Second destination", "0.00");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> submitWhenStarted(
                    ready, start, UUID.randomUUID(), sourceId, firstDestinationId, "700.00"));
            var second = executor.submit(() -> submitWhenStarted(
                    ready, start, UUID.randomUUID(), sourceId, secondDestinationId, "700.00"));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var firstResponse = first.get(15, TimeUnit.SECONDS);
            var secondResponse = second.get(15, TimeUnit.SECONDS);
            assertThat(List.of(firstResponse, secondResponse)).containsExactlyInAnyOrder(201, 422);
        }

        assertThat(accountBalance(sourceId)).isEqualByComparingTo("300.00");
        assertThat(List.of(accountBalance(firstDestinationId), accountBalance(secondDestinationId)))
                .containsExactlyInAnyOrder(new BigDecimal("700.00"), new BigDecimal("0.00"));
        assertThat(totalBalance()).isEqualByComparingTo("1000.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE status = 'COMPLETED'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE status = 'FAILED' AND failure_code = 'INSUFFICIENT_FUNDS'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(2);
    }

    @Test
    void oppositeDirectionTransfersCompleteWithoutDeadlockOrLostUpdates() throws Exception {
        var firstAccountId = createFundedAccount("First", "100.00");
        var secondAccountId = createFundedAccount("Second", "100.00");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> submitWhenStarted(
                    ready, start, UUID.randomUUID(), firstAccountId, secondAccountId, "10.00"));
            var second = executor.submit(() -> submitWhenStarted(
                    ready, start, UUID.randomUUID(), secondAccountId, firstAccountId, "20.00"));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(201);
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(201);
        }

        assertThat(accountBalance(firstAccountId)).isEqualByComparingTo("110.00");
        assertThat(accountBalance(secondAccountId)).isEqualByComparingTo("90.00");
        assertThat(totalBalance()).isEqualByComparingTo("200.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(4);
    }

    @Test
    void lockTimeoutReturnsRetryableErrorAndSameKeySucceedsAfterRelease() throws Exception {
        var sourceId = createFundedAccount("Source", "100.00");
        var destinationId = createFundedAccount("Destination", "0.00");
        var key = UUID.randomUUID();
        var lockAcquired = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var lockHolder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbcTemplate.queryForObject("SELECT id FROM accounts WHERE id = ? FOR UPDATE", Long.class, sourceId);
                lockAcquired.countDown();
                try {
                    if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release the test lock");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                var failure = submit(key, sourceId, destinationId, "10.00")
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("RETRYABLE_CONCURRENCY_FAILURE"))
                        .andReturn();
                var transferId = objectMapper.readTree(failure.getResponse().getContentAsString())
                        .get("transferId").asLong();

                assertThat(accountBalance(sourceId)).isEqualByComparingTo("100.00");
                assertThat(accountBalance(destinationId)).isEqualByComparingTo("0.00");
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM transfers WHERE id = ?", String.class, transferId)).isEqualTo("PENDING");
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isZero();
            } finally {
                releaseLock.countDown();
            }
            lockHolder.get(10, TimeUnit.SECONDS);
        }

        submit(key, sourceId, destinationId, "10.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(accountBalance(sourceId)).isEqualByComparingTo("90.00");
        assertThat(accountBalance(destinationId)).isEqualByComparingTo("10.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(2);
    }

    private int submitWhenStarted(CountDownLatch ready, CountDownLatch start, UUID key,
                                  long sourceId, long destinationId, String amount) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent transfer test did not start");
        }
        return submit(key, sourceId, destinationId, amount).andReturn().getResponse().getStatus();
    }

    private ResultActions submit(UUID key, long sourceId, long destinationId, String amount) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("{\"sourceAccountId\":%d,\"destinationAccountId\":%d,\"amount\":%s,\"currency\":\"MXN\"}"
                        .formatted(sourceId, destinationId, amount)));
    }

    private long createFundedAccount(String name, String balance) throws Exception {
        var result = mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content("{\"ownerName\":\"%s\",\"currency\":\"MXN\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        var id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        jdbcTemplate.update("UPDATE accounts SET balance = ? WHERE id = ?", balance, id);
        return id;
    }

    private BigDecimal accountBalance(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private BigDecimal totalBalance() {
        return jdbcTemplate.queryForObject("SELECT SUM(balance) FROM accounts", BigDecimal.class);
    }
}
