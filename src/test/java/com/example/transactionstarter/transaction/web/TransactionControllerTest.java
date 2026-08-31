package com.example.transactionstarter.transaction.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.transactionstarter.transaction.domain.Transaction;
import com.example.transactionstarter.transaction.domain.TransactionStatus;
import com.example.transactionstarter.transaction.domain.TransactionType;
import com.example.transactionstarter.transaction.error.DuplicateTransactionIdException;
import com.example.transactionstarter.transaction.error.InvalidStatusTransitionException;
import com.example.transactionstarter.transaction.error.TransactionNotFoundException;
import com.example.transactionstarter.transaction.error.TransactionRuleException;
import com.example.transactionstarter.transaction.service.TransactionService;
import com.example.transactionstarter.transaction.web.dto.CreateTransactionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web layer in isolation. The service is mocked, so these tests only prove
 * the controller and the {@code GlobalExceptionHandler} do their job: parse the
 * request, call the service, and turn its result (or its exception) into the
 * right status code and body. The rules themselves are tested elsewhere.
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final Instant T = Instant.parse("2026-01-15T09:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService service;

    private static Transaction transaction(String id, TransactionStatus status) {
        return new Transaction(id, "cust-1", new BigDecimal("125.50"), "GBP",
                TransactionType.CASH, status, T, T);
    }

    private static final String VALID_BODY = """
            {"transactionId":"txn-1","customerId":"cust-1","amount":125.50,"currency":"GBP","type":"CASH"}
            """;

    // --- create -----------------------------------------------------------

    @Test
    void createReturns201WithLocationHeaderAndBody() throws Exception {
        when(service.create(any(CreateTransactionRequest.class)))
                .thenReturn(transaction("txn-1", TransactionStatus.PENDING));

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/transactions/txn-1"))
                .andExpect(jsonPath("$.transactionId").value("txn-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createReturns400WhenTheBodyFailsValidationAndTheServiceIsNeverCalled() throws Exception {
        String badAmount = """
                {"transactionId":"txn-1","customerId":"cust-1","amount":-5,"currency":"GBP","type":"CASH"}
                """;

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(badAmount))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));

        verifyNoInteractions(service);
    }

    @Test
    void createReturns400OnAnUnknownEnumValue() throws Exception {
        String badType = """
                {"transactionId":"txn-1","customerId":"cust-1","amount":5,"currency":"GBP","type":"BITCOIN"}
                """;

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(badType))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createReturns409WhenTheServiceReportsADuplicate() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateTransactionIdException("txn-1"));

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createReturns422WhenTheServiceReportsABusinessRuleViolation() throws Exception {
        when(service.create(any())).thenThrow(new TransactionRuleException("Currency 'JPY' is not supported"));

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("JPY")));
    }

    // --- get one --------------------------------------------------------

    @Test
    void getReturns200WithTheTransaction() throws Exception {
        when(service.getById("txn-1")).thenReturn(transaction("txn-1", TransactionStatus.COMPLETED));

        mockMvc.perform(get("/api/transactions/txn-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("txn-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getReturns404WhenTheServiceThrowsNotFound() throws Exception {
        when(service.getById("missing")).thenThrow(new TransactionNotFoundException("missing"));

        mockMvc.perform(get("/api/transactions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("A malformed id in the URL is a 400, not a wasted lookup or a 500")
    void getReturns400WhenThePathIdIsMalformed() throws Exception {
        mockMvc.perform(get("/api/transactions/" + "x".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("transactionId"));

        verifyNoInteractions(service);
    }

    @Test
    void listReturns400WhenTheCustomerIdParamIsMalformed() throws Exception {
        mockMvc.perform(get("/api/transactions").param("customerId", "has spaces"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // --- update status ------------------------------------------------

    @Test
    void patchReturns200OnASuccessfulStatusChange() throws Exception {
        when(service.updateStatus("txn-1", TransactionStatus.COMPLETED))
                .thenReturn(transaction("txn-1", TransactionStatus.COMPLETED));

        mockMvc.perform(patch("/api/transactions/txn-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void patchReturns409WhenTheTransitionIsNotAllowed() throws Exception {
        when(service.updateStatus(eq("txn-1"), any()))
                .thenThrow(new InvalidStatusTransitionException("txn-1",
                        TransactionStatus.COMPLETED, TransactionStatus.PENDING));

        mockMvc.perform(patch("/api/transactions/txn-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void patchReturns400WhenTheStatusIsMissingFromTheBody() throws Exception {
        mockMvc.perform(patch("/api/transactions/txn-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).updateStatus(any(), any());
    }

    // --- list by customer -------------------------------------------

    @Test
    void listReturns200WithAnArray() throws Exception {
        when(service.getByCustomer("cust-1")).thenReturn(java.util.List.of(
                transaction("a-1", TransactionStatus.PENDING),
                transaction("a-2", TransactionStatus.COMPLETED)));

        mockMvc.perform(get("/api/transactions").param("customerId", "cust-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void listReturns400WhenTheCustomerIdParameterIsMissing() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
