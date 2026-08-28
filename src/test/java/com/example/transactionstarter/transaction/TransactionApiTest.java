package com.example.transactionstarter.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.transactionstarter.transaction.domain.TransactionStatus;
import com.example.transactionstarter.transaction.repo.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests for the four operations, exercised through HTTP against the
 * real in-memory database. Covers every case the challenge asks for plus the
 * status-update and by-customer paths.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository repository;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    private static String createBody(String id, String customerId, String amount,
                                     String currency, String type) {
        return """
                {
                  "transactionId": "%s",
                  "customerId": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "type": "%s"
                }
                """.formatted(id, customerId, amount, currency, type);
    }

    private void create(String id, String customerId, String amount,
                        String currency, String type) throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(id, customerId, amount, currency, type)))
                .andExpect(status().isCreated());
    }

    // --- A. Create -------------------------------------------------------------

    @Test
    void createsAValidTransactionAsPending() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("txn-1", "cust-1", "125.50", "GBP", "DEPOSIT")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/transactions/txn-1"))
                .andExpect(jsonPath("$.transactionId").value("txn-1"))
                .andExpect(jsonPath("$.amount").value(125.50))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(repository.findById("txn-1")).isPresent();
    }

    @Test
    void rejectsATransactionThatFailsValidationAndStoresNothing() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("txn-bad", "cust-1", "-5.00", "GBP", "DEPOSIT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));

        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsAnUnsupportedCurrency() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("txn-jpy", "cust-1", "10.00", "JPY", "DEPOSIT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("JPY")));

        assertThat(repository.count()).isZero();
    }

    @Test
    void acceptsEveryConfiguredCurrency() throws Exception {
        String[] currencies = {"GBP", "EUR", "USD", "INR"};
        for (int i = 0; i < currencies.length; i++) {
            create("cur-" + i, "cust-1", "10.00", currencies[i], "DEPOSIT");
        }
        assertThat(repository.count()).isEqualTo(currencies.length);
    }

    @Test
    void rejectsAnAmountOverTheLimit() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("txn-big", "cust-1", "10000.01", "GBP", "DEPOSIT")))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsAnUnknownTransactionType() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("txn-x", "cust-1", "10.00", "GBP", "CHARGEBACK")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CHARGEBACK")));
    }

    @Test
    void rejectsADuplicateTransactionId() throws Exception {
        create("txn-dup", "cust-1", "10.00", "GBP", "DEPOSIT");

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("txn-dup", "cust-2", "20.00", "EUR", "REFUND")))
                .andExpect(status().isConflict());

        // original is untouched
        assertThat(repository.findById("txn-dup")).get()
                .satisfies(t -> {
                    assertThat(t.getCustomerId()).isEqualTo("cust-1");
                    assertThat(t.getAmount().toPlainString()).isEqualTo("10.00");
                });
    }

    // --- B. Get --------------------------------------------------------------

    @Test
    void returns404ForATransactionThatDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/transactions/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returnsAnExistingTransaction() throws Exception {
        create("txn-2", "cust-9", "42.00", "EUR", "TRANSFER");

        mockMvc.perform(get("/api/transactions/txn-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("cust-9"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.type").value("TRANSFER"));
    }

    // --- C. Update status --------------------------------------------------------

    @Test
    void updatesStatusOnAnAllowedTransition() throws Exception {
        create("txn-3", "cust-1", "10.00", "GBP", "DEPOSIT");

        mockMvc.perform(patch("/api/transactions/txn-3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(repository.findById("txn-3")).get()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TransactionStatus.COMPLETED));
    }

    @Test
    void rejectsAForbiddenStatusTransitionAndLeavesTheTransactionUnchanged() throws Exception {
        create("txn-4", "cust-1", "10.00", "GBP", "DEPOSIT");
        mockMvc.perform(patch("/api/transactions/txn-4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/transactions/txn-4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict());

        assertThat(repository.findById("txn-4")).get()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TransactionStatus.COMPLETED));
    }

    @Test
    void returns404WhenUpdatingStatusOfAnUnknownTransaction() throws Exception {
        mockMvc.perform(patch("/api/transactions/ghost/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound());
    }

    // --- D. Get customer transactions ------------------------------------------

    @Test
    void returnsOnlyTheGivenCustomersTransactions() throws Exception {
        create("a-1", "alice", "10.00", "GBP", "DEPOSIT");
        create("a-2", "alice", "20.00", "GBP", "WITHDRAWAL");
        create("b-1", "bob", "30.00", "EUR", "DEPOSIT");

        mockMvc.perform(get("/api/transactions").param("customerId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].transactionId",
                        org.hamcrest.Matchers.containsInAnyOrder("a-1", "a-2")));
    }

    @Test
    void returnsAnEmptyArrayForACustomerWithNoTransactions() throws Exception {
        mockMvc.perform(get("/api/transactions").param("customerId", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returns400WhenCustomerIdParameterIsMissing() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isBadRequest());
    }
}
