package com.example.transactionstarter.transaction.error;

/**
 * A business rule on the transaction's own data was broken - the request was
 * well formed but not acceptable (e.g. currency not permitted, amount over the
 * limit). Maps to HTTP 422 (Unprocessable Entity).
 */
public class TransactionRuleException extends RuntimeException {

    public TransactionRuleException(String message) {
        super(message);
    }
}
