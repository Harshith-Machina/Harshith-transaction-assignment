package com.example.transactionstarter.transaction.repo;

import com.example.transactionstarter.transaction.domain.Transaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Transaction}. Spring Data provides the implementation.
 */
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** All transactions for one customer, oldest first. Empty list if none. */
    List<Transaction> findByCustomerIdOrderByCreatedAtAsc(String customerId);
}
