package com.example.transactionstarter.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.transactionstarter.transaction.TransactionProperties;
import com.example.transactionstarter.transaction.domain.Transaction;
import com.example.transactionstarter.transaction.domain.TransactionStatus;
import com.example.transactionstarter.transaction.domain.TransactionType;
import com.example.transactionstarter.transaction.error.DuplicateTransactionIdException;
import com.example.transactionstarter.transaction.error.InvalidStatusTransitionException;
import com.example.transactionstarter.transaction.error.TransactionNotFoundException;
import com.example.transactionstarter.transaction.error.TransactionRuleException;
import com.example.transactionstarter.transaction.repo.TransactionRepository;
import com.example.transactionstarter.transaction.web.dto.CreateTransactionRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The business rules in isolation. The repository is mocked and the clock is
 * fixed, so every test states one situation and checks one outcome without a
 * Spring context or a database. Format validation is assumed to have already
 * happened (it is covered by {@code CreateTransactionRequestValidationTest}).
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T09:30:00Z");

    @Mock
    private TransactionRepository repository;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        TransactionProperties properties = new TransactionProperties(
                Set.of("GBP", "EUR", "USD", "INR"), new BigDecimal("40000.00"));
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new TransactionService(repository, properties, fixedClock);
    }

    private static CreateTransactionRequest request(String id, String amount, String currency) {
        return new CreateTransactionRequest(id, "cust-1", new BigDecimal(amount), currency, TransactionType.CASH);
    }

    private static Transaction stored(String id, TransactionStatus status) {
        return new Transaction(id, "cust-1", new BigDecimal("10.00"), "GBP",
                TransactionType.CASH, status, NOW.minusSeconds(3600), NOW.minusSeconds(3600));
    }

    private void repositoryEchoesSavedEntity() {
        when(repository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    // --- create -------------------------------------------------------------

    @Test
    @DisplayName("A valid request is stored as PENDING with the fields from the request")
    void createStoresAPendingTransaction() {
        when(repository.existsById("txn-1")).thenReturn(false);
        repositoryEchoesSavedEntity();

        Transaction result = service.create(request("txn-1", "125.50", "GBP"));

        assertThat(result.getTransactionId()).isEqualTo("txn-1");
        assertThat(result.getCustomerId()).isEqualTo("cust-1");
        assertThat(result.getCurrency()).isEqualTo("GBP");
        assertThat(result.getType()).isEqualTo(TransactionType.CASH);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("createdAt and updatedAt come from the injected clock, not wall time")
    void createStampsTimestampsFromTheClock() {
        when(repository.existsById(any())).thenReturn(false);
        repositoryEchoesSavedEntity();

        Transaction result = service.create(request("txn-1", "10.00", "GBP"));

        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("An amount is padded to two decimal places before it is stored")
    void createNormalisesAmountScale() {
        when(repository.existsById(any())).thenReturn(false);
        repositoryEchoesSavedEntity();

        Transaction result = service.create(request("txn-1", "10", "GBP"));

        assertThat(result.getAmount()).isEqualByComparingTo("10.00");
        assertThat(result.getAmount().scale()).isEqualTo(2);
    }

    @Test
    void createRejectsACurrencyOutsideThePermittedSetAndWritesNothing() {
        assertThatThrownBy(() -> service.create(request("txn-1", "10.00", "JPY")))
                .isInstanceOf(TransactionRuleException.class)
                .hasMessageContaining("JPY");

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsAnAmountOverTheConfiguredMaximumAndWritesNothing() {
        assertThatThrownBy(() -> service.create(request("txn-1", "40000.01", "GBP")))
                .isInstanceOf(TransactionRuleException.class)
                .hasMessageContaining("40000.00");

        verify(repository, never()).save(any());
    }

    @Test
    void createAcceptsAnAmountExactlyOnTheMaximum() {
        when(repository.existsById(any())).thenReturn(false);
        repositoryEchoesSavedEntity();

        Transaction result = service.create(request("txn-1", "40000.00", "GBP"));

        assertThat(result.getAmount()).isEqualByComparingTo("40000.00");
    }

    @Test
    @DisplayName("A known ID is rejected before any write is attempted")
    void createRejectsADuplicateIdBeforeWriting() {
        when(repository.existsById("txn-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("txn-1", "10.00", "GBP")))
                .isInstanceOf(DuplicateTransactionIdException.class)
                .hasMessageContaining("txn-1");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("A unique-constraint violation on save (a lost race) becomes a duplicate error, not a 500")
    void createTranslatesADbUniqueViolationIntoADuplicateError() {
        when(repository.existsById("txn-1")).thenReturn(false);
        when(repository.save(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("unique_violation"));

        assertThatThrownBy(() -> service.create(request("txn-1", "10.00", "GBP")))
                .isInstanceOf(DuplicateTransactionIdException.class);
    }

    // --- getById ---------------------------------------------------------

    @Test
    void getByIdReturnsTheStoredTransaction() {
        when(repository.findById("txn-1")).thenReturn(Optional.of(stored("txn-1", TransactionStatus.PENDING)));

        assertThat(service.getById("txn-1").getTransactionId()).isEqualTo("txn-1");
    }

    @Test
    void getByIdThrowsWhenTheIdIsUnknown() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // --- updateStatus ---------------------------------------------------

    @Test
    @DisplayName("An allowed transition is applied and updatedAt is moved to now")
    void updateStatusAppliesAnAllowedTransition() {
        Transaction transaction = stored("txn-1", TransactionStatus.PENDING);
        when(repository.findById("txn-1")).thenReturn(Optional.of(transaction));
        repositoryEchoesSavedEntity();

        Transaction result = service.updateStatus("txn-1", TransactionStatus.COMPLETED);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result.getCreatedAt()).isEqualTo(NOW.minusSeconds(3600));
    }

    @Test
    void updateStatusRejectsAForbiddenTransitionAndWritesNothing() {
        when(repository.findById("txn-1")).thenReturn(Optional.of(stored("txn-1", TransactionStatus.COMPLETED)));

        assertThatThrownBy(() -> service.updateStatus("txn-1", TransactionStatus.PENDING))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void updateStatusThrowsWhenTheTransactionIsUnknown() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus("missing", TransactionStatus.COMPLETED))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    // --- getByCustomer -------------------------------------------------

    @Test
    void getByCustomerReturnsWhateverTheOrderedQueryReturns() {
        when(repository.findByCustomerIdOrderByCreatedAtAsc("cust-1")).thenReturn(List.of(
                stored("a-1", TransactionStatus.PENDING),
                stored("a-2", TransactionStatus.COMPLETED)));

        assertThat(service.getByCustomer("cust-1"))
                .extracting(Transaction::getTransactionId)
                .containsExactly("a-1", "a-2");
    }

    @Test
    void getByCustomerReturnsAnEmptyListForACustomerWithNothing() {
        when(repository.findByCustomerIdOrderByCreatedAtAsc("nobody")).thenReturn(List.of());

        assertThat(service.getByCustomer("nobody")).isEmpty();
    }
}
