package com.example.transactionstarter.transaction.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle state of a transaction, plus the rules for how it may change.
 *
 * <p>Allowed transitions:
 * <pre>
 *   PENDING   -&gt; COMPLETED | FAILED | CANCELLED
 *   COMPLETED -&gt; REVERSED
 *   FAILED    -&gt; (terminal)
 *   CANCELLED -&gt; (terminal)
 *   REVERSED  -&gt; (terminal)
 * </pre>
 *
 * <p>Reasoning: a new transaction is always {@link #PENDING} until it settles. From
 * PENDING it either succeeds ({@code COMPLETED}), fails ({@code FAILED}) or is pulled
 * before settling ({@code CANCELLED}). Only a settled transaction can be
 * {@code REVERSED}. FAILED, CANCELLED and REVERSED are end states. A transition to
 * the same status is not allowed - the caller asked for a change that is not one.
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REVERSED;

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(COMPLETED, FAILED, CANCELLED),
            COMPLETED, EnumSet.of(REVERSED),
            FAILED, EnumSet.noneOf(TransactionStatus.class),
            CANCELLED, EnumSet.noneOf(TransactionStatus.class),
            REVERSED, EnumSet.noneOf(TransactionStatus.class)
    );

    /** The status every newly created transaction starts in. */
    public static TransactionStatus initial() {
        return PENDING;
    }

    /** @return true if a transaction in this status may move to {@code target}. */
    public boolean canTransitionTo(TransactionStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** @return the statuses this status may move to (empty for a terminal status). */
    public Set<TransactionStatus> allowedNextStatuses() {
        return Set.copyOf(ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()));
    }
}
