package io.github.gdpe_franco.internaltransactions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gdpe_franco.internaltransactions.application.service.TransferRegistrationService;
import io.github.gdpe_franco.internaltransactions.application.port.ledger.LedgerStore;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.domain.account.ClabeAccountNumber;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransferApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private TransferRegistrationService transferRegistrationService;

    @MockitoSpyBean
    private LedgerStore ledgerStore;

    @BeforeEach
    void clearTransferData() {
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM transfers");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    void completesTransferAtomicallyAndReturnsBothLedgerEntries() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "1000.00");
        var destinationId = createFundedAccount("Destination", "MXN", "100.00");
        var result = submit(UUID.randomUUID(), sourceId, destinationId, "250.00", "MXN")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        var transferId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        assertThat(accountBalance(sourceId)).isEqualByComparingTo("750.00");
        assertThat(accountBalance(destinationId)).isEqualByComparingTo("350.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?", Integer.class, transferId)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/transfers/{id}", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.failureCode").doesNotExist());
        mockMvc.perform(get("/api/v1/accounts/{id}/ledger", sourceId).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amount").value(250.00))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
        mockMvc.perform(get("/api/v1/accounts/{id}/ledger", destinationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].type").value("CREDIT"));
    }

    @Test
    void ledgerPaginationIsStableForEntriesWithTheSameTimestamp() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "100.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        submit(UUID.randomUUID(), sourceId, destinationId, "10.00", "MXN").andExpect(status().isCreated());
        submit(UUID.randomUUID(), sourceId, destinationId, "10.00", "MXN").andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE ledger_entries SET created_at = '2026-01-01 00:00:00.000000'");

        var firstPage = ledgerPage(sourceId, 0)
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andReturn();
        var secondPage = ledgerPage(sourceId, 1)
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andReturn();
        var firstEntryId = entryId(firstPage);
        var secondEntryId = entryId(secondPage);

        assertThat(firstEntryId).isGreaterThan(secondEntryId);
    }

    @ParameterizedTest(name = "transfer business rule rejects: {0}")
    @MethodSource("representativeRejections")
    void persistsBusinessRejectionWithoutChangingBalancesOrLedger(
            String scenario, String failureCode, String amount, String sourceBalance,
            boolean missingDestination, boolean sameAccount) throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", sourceBalance);
        var destinationId = sameAccount ? sourceId
                : missingDestination ? 900_000_002L : createFundedAccount("Destination", "MXN", "0.00");
        var sourceBalanceBefore = accountBalance(sourceId);
        var destinationBalanceBefore = sameAccount || missingDestination ? null : accountBalance(destinationId);

        var result = submit(UUID.randomUUID(), sourceId, destinationId, amount, "MXN")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.failureCode").value(failureCode))
                .andExpect(jsonPath("$.transferId").isNumber())
                .andReturn();
        var transferId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("transferId").asLong();

        mockMvc.perform(get("/api/v1/transfers/{id}", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value(failureCode));
        assertThat(accountBalance(sourceId)).isEqualByComparingTo(sourceBalanceBefore);
        if (destinationBalanceBefore != null) {
            assertThat(accountBalance(destinationId)).isEqualByComparingTo(destinationBalanceBefore);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?", Integer.class, transferId)).isZero();
    }

    private static Stream<Arguments> representativeRejections() {
        return Stream.of(
                Arguments.of("same account", "SAME_ACCOUNT", "10.00", "100.00", false, true),
                Arguments.of("missing destination", "DESTINATION_ACCOUNT_NOT_FOUND", "10.00", "100.00", true, false),
                Arguments.of("insufficient funds", "INSUFFICIENT_FUNDS", "10.00", "1.00", false, false));
    }

    @Test
    void replaysSuccessfulTransferForSameKey() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "100.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        var key = UUID.randomUUID();
        var first = submit(key, sourceId, destinationId, "25.00", "MXN")
                .andExpect(status().isCreated()).andReturn();
        var transferId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        submit(key, sourceId, destinationId, "25", "MXN")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(transferId));
        assertThat(accountBalance(sourceId)).isEqualByComparingTo("75.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectsReusedIdempotencyKeyWithDifferentDetails() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "100.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        var key = UUID.randomUUID();
        submit(key, sourceId, destinationId, "25.00", "MXN").andExpect(status().isCreated());

        submit(key, sourceId, destinationId, "30.00", "MXN")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(accountBalance(sourceId)).isEqualByComparingTo("75.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(2);
    }

    @Test
    void replaysFailedTransferForSameKey() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "1.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        var key = UUID.randomUUID();
        var first = submit(key, sourceId, destinationId, "2.00", "MXN")
                .andExpect(status().isUnprocessableEntity()).andReturn();
        var transferId = objectMapper.readTree(first.getResponse().getContentAsString()).get("transferId").asLong();

        submit(key, sourceId, destinationId, "2.00", "MXN")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.transferId").value(transferId))
                .andExpect(jsonPath("$.failureCode").value("INSUFFICIENT_FUNDS"));
        assertThat(accountBalance(sourceId)).isEqualByComparingTo("1.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isZero();
    }

    @Test
    void resumesPendingAttemptAfterRegistrationInterruption() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "100.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        var key = UUID.randomUUID();
        var pending = transferRegistrationService.register(new TransferRequest(key, sourceId, destinationId,
                new BigDecimal("25.00"), AccountCurrency.MXN));

        submit(key, sourceId, destinationId, "25.00", "MXN")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(pending.id()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(accountBalance(sourceId)).isEqualByComparingTo("75.00");
        assertThat(accountBalance(destinationId)).isEqualByComparingTo("25.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(2);
    }

    @Test
    void retryRevalidatesCurrentBalanceAndKeepsTheStoredBusinessFailure() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "100.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        var key = UUID.randomUUID();
        var pending = transferRegistrationService.register(new TransferRequest(key, sourceId, destinationId,
                new BigDecimal("25.00"), AccountCurrency.MXN));
        jdbcTemplate.update("UPDATE accounts SET balance = 10.00 WHERE id = ?", sourceId);

        var failed = submit(key, sourceId, destinationId, "25.00", "MXN")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.failureCode").value("INSUFFICIENT_FUNDS"))
                .andReturn();
        assertThat(objectMapper.readTree(failed.getResponse().getContentAsString())
                .get("transferId").asLong()).isEqualTo(pending.id());

        jdbcTemplate.update("UPDATE accounts SET balance = 100.00 WHERE id = ?", sourceId);
        submit(key, sourceId, destinationId, "25.00", "MXN")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.transferId").value(pending.id()))
                .andExpect(jsonPath("$.failureCode").value("INSUFFICIENT_FUNDS"));
        assertThat(accountBalance(sourceId)).isEqualByComparingTo("100.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isZero();
    }

    @Test
    void pendingAttemptRejectsReusedKeyWithDifferentDetails() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "100.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        var key = UUID.randomUUID();
        var pending = transferRegistrationService.register(new TransferRequest(key, sourceId, destinationId,
                new BigDecimal("25.00"), AccountCurrency.MXN));

        submit(key, sourceId, destinationId, "30.00", "MXN")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transfers WHERE id = ?", String.class, pending.id())).isEqualTo("PENDING");
        assertThat(accountBalance(sourceId)).isEqualByComparingTo("100.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isZero();
    }

    @Test
    void concurrentRetriesOfPendingAttemptProduceOneTransfer() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "100.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        var key = UUID.randomUUID();
        var pending = transferRegistrationService.register(new TransferRequest(key, sourceId, destinationId,
                new BigDecimal("25.00"), AccountCurrency.MXN));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> submitWhenStarted(ready, start, key, sourceId, destinationId));
            var second = executor.submit(() -> submitWhenStarted(ready, start, key, sourceId, destinationId));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var firstResponse = first.get(10, TimeUnit.SECONDS);
            var secondResponse = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResponse.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResponse.getResponse().getStatus()).isEqualTo(200);
            assertThat(responseTransferId(firstResponse)).isEqualTo(pending.id());
            assertThat(responseTransferId(secondResponse)).isEqualTo(pending.id());
        }
        assertThat(accountBalance(sourceId)).isEqualByComparingTo("75.00");
        assertThat(accountBalance(destinationId)).isEqualByComparingTo("25.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentDuplicateSubmissionsCreateOnlyOneTransfer() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "100.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        var key = UUID.randomUUID();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> submitWhenStarted(ready, start, key, sourceId, destinationId));
            var second = executor.submit(() -> submitWhenStarted(ready, start, key, sourceId, destinationId));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var firstResponse = first.get(10, TimeUnit.SECONDS);
            var secondResponse = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResponse.getResponse().getStatus()).isIn(200, 201);
            assertThat(secondResponse.getResponse().getStatus()).isIn(200, 201);
            var firstId = responseTransferId(firstResponse);
            var secondId = responseTransferId(secondResponse);

            assertThat(firstId).isEqualTo(secondId);
            assertThat(accountBalance(sourceId)).isEqualByComparingTo("75.00");
            assertThat(accountBalance(destinationId)).isEqualByComparingTo("25.00");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void rejectsMalformedSubmissionWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType("application/json")
                        .content("{\"sourceAccountId\":1,\"destinationAccountId\":2,\"amount\":1,\"currency\":\"MXN\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isZero();
    }

    @ParameterizedTest(name = "reports validation error on transfer field {0}")
    @MethodSource("invalidTransferFields")
    void reportsFieldSpecificErrorsForInvalidTransferRequests(String field, String request) throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).isNotEmpty());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isZero();
    }

    private static Stream<Arguments> invalidTransferFields() {
        return Stream.of(
                Arguments.of("currency", "{\"sourceAccountId\":1,\"destinationAccountId\":2,\"amount\":1,\"currency\":\"MXN!\"}"),
                Arguments.of("amount", "{\"sourceAccountId\":1,\"destinationAccountId\":2,\"amount\":1.001,\"currency\":\"MXN\"}"),
                Arguments.of("sourceAccountId", "{\"sourceAccountId\":0,\"destinationAccountId\":2,\"amount\":1,\"currency\":\"MXN\"}"));
    }

    @Test
    void returnsNotFoundForUnknownTransfer() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Transfer not found"));
    }

    @Test
    void keepsAttemptPendingAfterProcessingErrorAndCompletesItOnRetry() throws Exception {
        var sourceId = createFundedAccount("Source", "MXN", "100.00");
        var destinationId = createFundedAccount("Destination", "MXN", "0.00");
        var key = UUID.randomUUID();
        var pending = transferRegistrationService.register(new TransferRequest(key, sourceId, destinationId,
                new BigDecimal("10.00"), AccountCurrency.MXN));
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new org.springframework.dao.DataAccessResourceFailureException("Injected ledger write failure");
        }).when(ledgerStore).saveAll(anyList());

        var failed = submit(key, sourceId, destinationId, "10.00", "MXN")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("TRANSFER_PROCESSING_ERROR"))
                .andExpect(jsonPath("$.transferId").value(pending.id()))
                .andReturn();
        assertThat(objectMapper.readTree(failed.getResponse().getContentAsString())
                .get("detail").asText()).contains("same Idempotency-Key");

        assertThat(accountBalance(sourceId)).isEqualByComparingTo("100.00");
        assertThat(accountBalance(destinationId)).isEqualByComparingTo("0.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM transfers", String.class)).isEqualTo("PENDING");

        reset(ledgerStore);
        submit(key, sourceId, destinationId, "10.00", "MXN")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(pending.id()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(accountBalance(sourceId)).isEqualByComparingTo("90.00");
        assertThat(accountBalance(destinationId)).isEqualByComparingTo("10.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(2);
    }

    @Test
    void registrationErrorDoesNotReturnAFabricatedTransferId() throws Exception {
        doThrow(new DataAccessResourceFailureException("injected registration failure"))
                .when(transferRegistrationService).register(any(TransferRequest.class));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"sourceAccountId\":1,\"destinationAccountId\":2,\"amount\":1.00,\"currency\":\"MXN\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("TRANSFER_REGISTRATION_ERROR"))
                .andExpect(jsonPath("$.transferId").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isZero();
    }

    @Test
    void returnsEmptyLedgerAndRejectsAnOversizedPage() throws Exception {
        var accountId = createAccount("Empty ledger");
        ledgerPage(accountId, 0)
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0));
        mockMvc.perform(get("/api/v1/accounts/{id}/ledger", accountId).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    private ResultActions submit(UUID key, long sourceId, long destinationId, String amount, String currency)
            throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(transferRequest(sourceId, destinationId, amount, currency)));
    }

    private ResultActions ledgerPage(long accountId, int page) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/{id}/ledger", accountId)
                .param("page", Integer.toString(page)).param("size", "1"));
    }

    private long entryId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("entries").get(0).get("id").asLong();
    }

    private long responseTransferId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createFundedAccount(String ownerName, String currency, String balance) throws Exception {
        var id = createAccount(ownerName, currency);
        jdbcTemplate.update("UPDATE accounts SET balance = ? WHERE id = ?", balance, id);
        return id;
    }

    private long createAccount(String ownerName) throws Exception {
        return createAccount(ownerName, "MXN");
    }

    private long createAccount(String ownerName, String currency) throws Exception {
        var result = mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content("{\"ownerName\":\"%s\",\"currency\":\"%s\"}"
                                .formatted(ownerName, currency)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void setAccountStatus(long accountId, String status) {
        jdbcTemplate.update("UPDATE accounts SET status = ? WHERE id = ?", status, accountId);
    }

    private BigDecimal accountBalance(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private String transferRequest(long sourceId, long destinationId, String amount, String currency) {
        return "{\"sourceAccountId\":%d,\"destinationAccountId\":%d,\"amount\":%s,\"currency\":\"%s\"}"
                .formatted(sourceId, destinationId, amount, currency);
    }

    private MvcResult submitWhenStarted(CountDownLatch ready, CountDownLatch start, UUID key, long sourceId,
                                        long destinationId) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent transfer test did not start");
        }
        return submit(key, sourceId, destinationId, "25.00", "MXN").andReturn();
    }
}
