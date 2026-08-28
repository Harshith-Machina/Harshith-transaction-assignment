package com.example.transactionstarter.transaction.error;

/** Raised when creating a transaction whose id is already in use. Maps to HTTP 409. */
public class DuplicateTransactionIdException extends RuntimeException {

    public DuplicateTransactionIdException(String transactionId) {
        super("A transaction with id '" + transactionId + "' already exists");
    }
}
