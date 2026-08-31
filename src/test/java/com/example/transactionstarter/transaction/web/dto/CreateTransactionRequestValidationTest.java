package com.example.transactionstarter.transaction.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.transactionstarter.transaction.domain.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises the Bean Validation annotations on {@link CreateTransactionRequest}
 * directly, with no Spring context. These are the "format" rules - the ones that
 * do not need the database or the configured limits - and this is the cheapest
 * place to prove each one fires on exactly the input it should.
 */
class CreateTransactionRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static CreateTransactionRequest valid() {
        return new CreateTransactionRequest("txn-1", "cust-1", new BigDecimal("125.50"), "GBP", TransactionType.CASH);
    }

    private Set<String> violatedFields(CreateTransactionRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    // --- a well-formed request -----------------------------------------------

    @Test
    void aFullyPopulatedRequestHasNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    // --- transactionId -------------------------------------------------------

    @Test
    void blankTransactionIdIsRejected() {
        assertThat(violatedFields(new CreateTransactionRequest("  ", "cust-1", new BigDecimal("1.00"), "GBP", TransactionType.CASH)))
                .contains("transactionId");
    }

    @Test
    void transactionIdWithIllegalCharactersIsRejected() {
        assertThat(violatedFields(new CreateTransactionRequest("txn 1/2", "cust-1", new BigDecimal("1.00"), "GBP", TransactionType.CASH)))
                .contains("transactionId");
    }

    @Test
    void transactionIdOver64CharactersIsRejected() {
        String tooLong = "t".repeat(65);
        assertThat(violatedFields(new CreateTransactionRequest(tooLong, "cust-1", new BigDecimal("1.00"), "GBP", TransactionType.CASH)))
                .contains("transactionId");
    }

    // --- customerId --------------------------------------------------------

    @Test
    void blankCustomerIdIsRejected() {
        assertThat(violatedFields(new CreateTransactionRequest("txn-1", "", new BigDecimal("1.00"), "GBP", TransactionType.CASH)))
                .contains("customerId");
    }

    // --- amount -----------------------------------------------------------

    @Test
    void nullAmountIsRejected() {
        assertThat(violatedFields(new CreateTransactionRequest("txn-1", "cust-1", null, "GBP", TransactionType.CASH)))
                .contains("amount");
    }

    @Test
    void zeroAmountIsRejected() {
        assertThat(violatedFields(new CreateTransactionRequest("txn-1", "cust-1", new BigDecimal("0.00"), "GBP", TransactionType.CASH)))
                .contains("amount");
    }

    @Test
    void negativeAmountIsRejected() {
        assertThat(violatedFields(new CreateTransactionRequest("txn-1", "cust-1", new BigDecimal("-0.01"), "GBP", TransactionType.CASH)))
                .contains("amount");
    }

    @Test
    void amountWithMoreThanTwoDecimalPlacesIsRejected() {
        assertThat(violatedFields(new CreateTransactionRequest("txn-1", "cust-1", new BigDecimal("1.005"), "GBP", TransactionType.CASH)))
                .contains("amount");
    }

    @Test
    void amountValidationDoesNotComplainAboutTheConfiguredMaximum() {
        // 1,000,000 is over the business limit but that rule lives in the service,
        // not here - the annotation only checks shape.
        assertThat(validator.validate(
                new CreateTransactionRequest("txn-1", "cust-1", new BigDecimal("1000000.00"), "GBP", TransactionType.CASH)))
                .isEmpty();
    }

    // --- currency --------------------------------------------------------

    @ParameterizedTest(name = "currency \"{0}\" is rejected")
    @ValueSource(strings = {" ", "gbp", "GB", "POUND", "G8P", "12 "})
    @DisplayName("Currency has to be exactly three uppercase letters")
    void badlyShapedCurrencyIsRejected(String currency) {
        assertThat(violatedFields(new CreateTransactionRequest("txn-1", "cust-1", new BigDecimal("1.00"), currency, TransactionType.CASH)))
                .contains("currency");
    }

    // --- type -----------------------------------------------------------

    @Test
    void nullTypeIsRejected() {
        assertThat(violatedFields(new CreateTransactionRequest("txn-1", "cust-1", new BigDecimal("1.00"), "GBP", null)))
                .contains("type");
    }

    // --- messages -------------------------------------------------------

    @Test
    void theCurrencyMessageNamesTheExpectedFormat() {
        Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(
                new CreateTransactionRequest("txn-1", "cust-1", new BigDecimal("1.00"), "gbp", TransactionType.CASH));

        assertThat(violations)
                .anySatisfy(v -> assertThat(v.getMessage()).contains("3-letter"));
    }
}
