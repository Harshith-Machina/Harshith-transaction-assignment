package com.example.transactionstarter.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A customer transaction.
 *
 * <p>The {@code transactionId} is supplied by the caller and used as the primary
 * key: the exercise requires rejecting an id that already exists, so it must be a
 * natural key rather than a generated one.
 *
 * <p>{@code amount} is a {@link BigDecimal} (never {@code double}) and is stored
 * with a fixed scale of 2 so money comparisons and responses are exact.
 */
@Entity
@Table(name = "transactions", indexes = @Index(name = "idx_transaction_customer", columnList = "customerId"))
public class Transaction {

    @Id
    @Column(nullable = false, updatable = false, length = 64)
    private String transactionId;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Transaction() {
    }

    public Transaction(String transactionId,
                       String customerId,
                       BigDecimal amount,
                       String currency,
                       TransactionType type,
                       TransactionStatus status,
                       Instant createdAt,
                       Instant updatedAt) {
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Move this transaction to {@code newStatus}, recording the time of the change.
     *
     * @throws IllegalArgumentException if the transition is not allowed - callers
     *         should check {@link TransactionStatus#canTransitionTo} first and raise
     *         a domain-specific error; this is only a last-line guard.
     */
    public void changeStatus(TransactionStatus newStatus, Instant when) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException(
                    "Cannot move transaction from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = when;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transaction other)) {
            return false;
        }
        return Objects.equals(transactionId, other.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }
}
