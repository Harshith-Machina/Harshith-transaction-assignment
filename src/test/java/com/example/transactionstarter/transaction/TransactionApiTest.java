package com.example.transactionstarter.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.transactionstarter.transaction.domain.TransactionStatus;
import com.example.transactionstarter.transaction.repo.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whole-stack integration: real HTTP, real Spring wiring, real in-memory H2,
 * nothing mocked. Each individual layer is proved on its own in the unit and
 * slice tests ({@code TransactionStatusTest},
 * {@code CreateTransactionRequestValidationTest}, {@code TransactionServiceTest},
 * {@code TransactionControllerTest}). This class proves the layers are wired
 * together and that data written by one request is really there for the next.
 *
 * <p>{@code @Transactional} rolls each test back, so order does not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository repository;

    private static String body(String id, String customerId, String amount, String currency, String type) {
        return """
                { "transactionId": "%s", "customerId": "%s", "amount": %s, "currency": "%s", "type": "%s" }
                """.formatted(id, customerId, amount, currency, type);
    }

    private void create(String id, String customerId, String amount, String currency, String type) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(id, customerId, amount, currency, type)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A transaction can be created, read back, moved through its lifecycle, and listed for its customer")
    void completeTransactionLifecycle() throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body("txn-1", "cust-1", "125.50", "GBP", "CASH")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/transactions/txn-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // reading it back proves the row was persisted, not just echoed
        mockMvc.perform(get("/api/transactions/txn-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("cust-1"))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.type").value("CASH"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(patch("/api/transactions/txn-1/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/transactions").param("customerId", "cust-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value("txn-1"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("A rejected create leaves nothing behind")
    void invalidInputIsNeverStored() throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body("txn-bad", "cust-1", "-5.00", "GBP", "CASH")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/transactions/txn-bad")).andExpect(status().isNotFound());
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Reusing a transaction ID is refused with 409 and the original is untouched")
    void duplicateTransactionIdIsRejected() throws Exception {
        create("txn-dup", "cust-1", "10.00", "GBP", "CASH");

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body("txn-dup", "cust-2", "20.00", "EUR", "ONLINE")))
                .andExpect(status().isConflict());

        assertThat(repository.findById("txn-dup")).get().satisfies(t -> {
            assertThat(t.getCustomerId()).isEqualTo("cust-1");
            assertThat(t.getAmount().toPlainString()).isEqualTo("10.00");
        });
    }

    @Test
    @DisplayName("A business-rule failure (unsupported currency) surfaces end to end as 422")
    void businessRuleFailureSurfacesAs422() throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body("txn-jpy", "cust-1", "10.00", "JPY", "CASH")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("JPY")));

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("A settled transaction cannot be reopened, and the row does not change")
    void aSettledTransactionCannotChangeAgain() throws Exception {
        create("txn-3", "cust-1", "10.00", "GBP", "CASH");
        mockMvc.perform(patch("/api/transactions/txn-3/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/transactions/txn-3/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVERSED\"}")).andExpect(status().isOk());

        mockMvc.perform(patch("/api/transactions/txn-3/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict());

        assertThat(repository.findById("txn-3")).get()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TransactionStatus.REVERSED));
    }

    @Test
    @DisplayName("One customer never sees another customer's transactions, and they come back oldest first")
    void customersAreKeptSeparateAndOrdered() throws Exception {
        create("a-1", "alice", "10.00", "GBP", "CASH");
        create("a-2", "alice", "20.00", "GBP", "CARD");
        create("b-1", "bob", "30.00", "EUR", "CASH");

        mockMvc.perform(get("/api/transactions").param("customerId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].transactionId", contains("a-1", "a-2")));

        mockMvc.perform(get("/api/transactions").param("customerId", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownTransactionReturns404() throws Exception {
        mockMvc.perform(get("/api/transactions/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("An unknown URL is a plain 404, not a logged 500")
    void anUnknownUrlIsAPlain404() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
