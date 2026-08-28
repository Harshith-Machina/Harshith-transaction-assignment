package com.example.transactionstarter.transaction.service;

import com.example.transactionstarter.transaction.TransactionProperties;
import com.example.transactionstarter.transaction.domain.Transaction;
import com.example.transactionstarter.transaction.domain.TransactionStatus;
import com.example.transactionstarter.transaction.error.DuplicateTransactionIdException;
import com.example.transactionstarter.transaction.error.InvalidStatusTransitionException;
import com.example.transactionstarter.transaction.error.TransactionNotFoundException;
import com.example.transactionstarter.transaction.error.TransactionRuleException;
import com.example.transactionstarter.transaction.repo.TransactionRepository;
import com.example.transactionstarter.transaction.web.dto.CreateTransactionRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All business rules for transactions live here. The controller does HTTP, the
 * repository does data access, and this class decides what is allowed.
 */
@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final TransactionProperties properties;
    private final Clock clock;

    public TransactionService(TransactionRepository repository,
                              TransactionProperties properties,
                              Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Validate and store a new transaction.
     *
     * <p>Rejects: a currency outside the permitted set, an amount above the limit,
     * and an id that is already in use. Format rules (blank fields, non-positive
     * amount, decimal places) are already enforced by Bean Validation before we
     * get here. New transactions always start as {@link TransactionStatus#PENDING}.
     */
    @Transactional
    public Transaction create(CreateTransactionRequest request) {
        if (!properties.isCurrencyAllowed(request.currency())) {
            throw new TransactionRuleException("Currency '" + request.currency()
                    + "' is not supported. Permitted: " + properties.allowedCurrencies());
        }
        BigDecimal amount = normalise(request.amount());
        if (!properties.isWithinLimit(amount)) {
            throw new TransactionRuleException("Amount " + amount.toPlainString()
                    + " exceeds the maximum of " + properties.maxAmountDisplay());
        }
        if (repository.existsById(request.transactionId())) {
            throw new DuplicateTransactionIdException(request.transactionId());
        }

        Instant now = clock.instant();
        Transaction transaction = new Transaction(
                request.transactionId(),
                request.customerId(),
                amount,
                request.currency(),
                request.type(),
                TransactionStatus.initial(),
                now,
                now);
        try {
            return repository.save(transaction);
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent create with the same id.
            throw new DuplicateTransactionIdException(request.transactionId());
        }
    }

    /** @throws TransactionNotFoundException if the id is unknown. */
    @Transactional(readOnly = true)
    public Transaction getById(String transactionId) {
        return repository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    /**
     * Move a transaction to {@code targetStatus}.
     *
     * @throws TransactionNotFoundException      if the id is unknown
     * @throws InvalidStatusTransitionException  if the change is not allowed
     */
    @Transactional
    public Transaction updateStatus(String transactionId, TransactionStatus targetStatus) {
        Transaction transaction = getById(transactionId);
        if (!transaction.getStatus().canTransitionTo(targetStatus)) {
            throw new InvalidStatusTransitionException(
                    transactionId, transaction.getStatus(), targetStatus);
        }
        transaction.changeStatus(targetStatus, clock.instant());
        return repository.save(transaction);
    }

    /** All transactions for a customer, oldest first. Empty list if the customer has none. */
    @Transactional(readOnly = true)
    public List<Transaction> getByCustomer(String customerId) {
        return repository.findByCustomerIdOrderByCreatedAtAsc(customerId);
    }

    private static BigDecimal normalise(BigDecimal amount) {
        // @Digits has already guaranteed at most 2 decimal places, so this only
        // pads (e.g. 10 -> 10.00) and never has to round.
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
