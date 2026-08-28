package com.example.transactionstarter.transaction.domain;

/**
 * How the customer paid.
 *
 * <p>This set is a deliberate choice for a shop-counter service (the brief leaves
 * "Transaction Type" for the candidate to define). It is a code change here rather
 * than configuration because each method usually grows its own behaviour later
 * (settlement timing, fees, reconciliation).
 */
public enum TransactionType {
    CASH,
    CARD,
    UPI,
    ONLINE
}
