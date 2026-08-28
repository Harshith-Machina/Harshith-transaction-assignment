package com.example.transactionstarter.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for the status-transition rules. No Spring context - this is plain
 * logic and should be tested as such.
 */
class TransactionStatusTest {

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(TransactionStatus.PENDING, TransactionStatus.COMPLETED),
                Arguments.of(TransactionStatus.PENDING, TransactionStatus.FAILED),
                Arguments.of(TransactionStatus.PENDING, TransactionStatus.CANCELLED),
                Arguments.of(TransactionStatus.COMPLETED, TransactionStatus.REVERSED));
    }

    static Stream<Arguments> forbiddenTransitions() {
        return Stream.of(
                Arguments.of(TransactionStatus.PENDING, TransactionStatus.REVERSED),
                Arguments.of(TransactionStatus.COMPLETED, TransactionStatus.PENDING),
                Arguments.of(TransactionStatus.COMPLETED, TransactionStatus.FAILED),
                Arguments.of(TransactionStatus.FAILED, TransactionStatus.COMPLETED),
                Arguments.of(TransactionStatus.CANCELLED, TransactionStatus.PENDING),
                Arguments.of(TransactionStatus.REVERSED, TransactionStatus.COMPLETED));
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void permitsExpectedTransitions(TransactionStatus from, TransactionStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("forbiddenTransitions")
    void rejectsUnexpectedTransitions(TransactionStatus from, TransactionStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TransactionStatus.class)
    void neverPermitsATransitionToItself(TransactionStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    void terminalStatusesHaveNoOnwardTransitions() {
        assertThat(TransactionStatus.FAILED.allowedNextStatuses()).isEmpty();
        assertThat(TransactionStatus.CANCELLED.allowedNextStatuses()).isEmpty();
        assertThat(TransactionStatus.REVERSED.allowedNextStatuses()).isEmpty();
    }

    @Test
    void newTransactionsStartPending() {
        assertThat(TransactionStatus.initial()).isEqualTo(TransactionStatus.PENDING);
    }
}
