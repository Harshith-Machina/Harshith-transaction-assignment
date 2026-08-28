package com.example.transactionstarter.transaction.domain;

/**
 * The kind of movement a transaction represents.
 *
 * <p>This set is fixed by the exercise variant. Changing the permitted types is a
 * code change here rather than configuration, because each value usually carries
 * its own behaviour later on (fees, limits, reporting).
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    REFUND
}
