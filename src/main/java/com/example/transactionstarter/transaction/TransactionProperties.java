package com.example.transactionstarter.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Business limits that the exercise variant controls: which currencies are
 * accepted and the largest amount a single transaction may hold.
 *
 * <p>Kept in configuration ({@code application.yml}, prefix {@code transaction})
 * so that adjusting a variant is a one-line change and so tests can override it.
 * Values are validated on startup - a bad configuration fails fast.
 */
@ConfigurationProperties(prefix = "transaction")
@Validated
public record TransactionProperties(

        @NotEmpty Set<String> allowedCurrencies,

        @NotNull @DecimalMin(value = "0.01") BigDecimal maxAmount) {

    public boolean isCurrencyAllowed(String currency) {
        return allowedCurrencies.contains(currency);
    }

    public boolean isWithinLimit(BigDecimal amount) {
        return amount.compareTo(maxAmount) <= 0;
    }
}
