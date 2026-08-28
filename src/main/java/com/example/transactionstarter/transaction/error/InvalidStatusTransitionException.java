package com.example.transactionstarter.transaction.error;

import com.example.transactionstarter.transaction.domain.TransactionStatus;

/** Raised when a requested status change is not allowed. Maps to HTTP 409. */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String transactionId,
                                            TransactionStatus from,
                                            TransactionStatus to) {
        super("Transaction '" + transactionId + "' cannot move from " + from + " to " + to
                + ". Allowed from " + from + ": " + from.allowedNextStatuses());
    }
}
