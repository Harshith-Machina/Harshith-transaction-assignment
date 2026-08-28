package com.example.transactionstarter.transaction.web.dto;

import com.example.transactionstarter.transaction.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Incoming body for "create transaction".
 *
 * <p>Only structural/format rules live here as annotations. Rules that need the
 * database or the configured limits (id uniqueness, currency whitelist, amount
 * ceiling) are enforced in the service. Status is deliberately not accepted - the
 * server always starts a transaction as PENDING.
 */
public record CreateTransactionRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$",
                message = "must be 1-64 characters of letters, digits or hyphens")
        String transactionId,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$",
                message = "must be 1-64 characters of letters, digits or hyphens")
        String customerId,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = false, message = "must be greater than 0")
        @Digits(integer = 17, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code")
        String currency,

        @NotNull(message = "must be one of DEPOSIT, WITHDRAWAL, TRANSFER, REFUND")
        TransactionType type) {
}
