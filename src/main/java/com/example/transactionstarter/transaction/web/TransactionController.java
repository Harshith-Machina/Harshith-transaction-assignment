package com.example.transactionstarter.transaction.web;

import com.example.transactionstarter.transaction.domain.Transaction;
import com.example.transactionstarter.transaction.service.TransactionService;
import com.example.transactionstarter.transaction.web.dto.CreateTransactionRequest;
import com.example.transactionstarter.transaction.web.dto.TransactionResponse;
import com.example.transactionstarter.transaction.web.dto.UpdateStatusRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP layer for the four transaction operations. Deliberately thin: it converts
 * to/from DTOs and delegates every decision to {@link TransactionService}.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /** A. Create transaction. 201 with a Location header, or 400 / 409. */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        Transaction created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/transactions/" + created.getTransactionId()))
                .body(TransactionResponse.from(created));
    }

    /** B. Get one transaction by id. 200, or 404. */
    @GetMapping("/{transactionId}")
    public TransactionResponse getById(@PathVariable String transactionId) {
        return TransactionResponse.from(service.getById(transactionId));
    }

    /** C. Update transaction status. 200, or 404 / 409 / 400. */
    @PatchMapping("/{transactionId}/status")
    public TransactionResponse updateStatus(@PathVariable String transactionId,
                                            @Valid @RequestBody UpdateStatusRequest request) {
        return TransactionResponse.from(service.updateStatus(transactionId, request.status()));
    }

    /** D. Get all transactions for a customer. 200 with an array (empty if none). */
    @GetMapping
    public List<TransactionResponse> getByCustomer(@RequestParam String customerId) {
        return service.getByCustomer(customerId).stream()
                .map(TransactionResponse::from)
                .toList();
    }
}
