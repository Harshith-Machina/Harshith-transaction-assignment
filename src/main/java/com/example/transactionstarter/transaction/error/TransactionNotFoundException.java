package com.example.transactionstarter.transaction.error;

/** Raised when a transaction id does not exist. Maps to HTTP 404. */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String transactionId) {
        super("No transaction found with id '" + transactionId + "'");
    }
}
