package com.example.transactionstarter.transaction.web.dto;

import com.example.transactionstarter.transaction.domain.TransactionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Incoming body for "update transaction status", e.g. {@code {"status":"COMPLETED"}}.
 * An unknown status string is rejected as a malformed body (400) before this
 * record is built.
 */
public record UpdateStatusRequest(

        @NotNull(message = "must be one of PENDING, COMPLETED, FAILED, CANCELLED, REVERSED")
        TransactionStatus status) {
}
