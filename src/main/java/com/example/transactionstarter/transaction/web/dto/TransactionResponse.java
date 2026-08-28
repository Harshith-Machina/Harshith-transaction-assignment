package com.example.transactionstarter.transaction.web.dto;

import com.example.transactionstarter.transaction.domain.Transaction;
import com.example.transactionstarter.transaction.domain.TransactionStatus;
import com.example.transactionstarter.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outgoing view of a transaction. Separate from the entity so the API contract
 * does not change just because the persistence model does.
 */
public record TransactionResponse(
        String transactionId,
        String customerId,
        BigDecimal amount,
        String currency,
        TransactionType type,
        TransactionStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getTransactionId(),
                t.getCustomerId(),
                t.getAmount(),
                t.getCurrency(),
                t.getType(),
                t.getStatus(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
