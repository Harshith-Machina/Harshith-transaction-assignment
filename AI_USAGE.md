# AI Usage Disclosure

> This describes how the code in this repository was produced. Read it against the
> commit history (`git log`) and the code before the interview, and adjust any
> wording that does not match your own account of the work. Do not claim anything
> here you cannot walk through.

## Tools used

- **Claude (Anthropic), via the Claude Code CLI** — used as a pair programmer to go
  from the challenge document to a working implementation, and to draft the docs.

## What it was used for

- Reading the challenge brief and the starter project, then proposing a structure
  (package layout, layering, where each rule belongs) before any code was written.
- Generating the first version of: the `Transaction` entity and the two enums, the
  repository, `TransactionService`, the controller, the request/response DTOs, the
  exception types and the `@RestControllerAdvice`, the `@ConfigurationProperties`
  binding, and both test classes.
- Building the optional static web console (`src/main/resources/static/`).
- Drafting `README.md`, `docs/design.html` and this disclosure.

## What the AI generated or suggested that is significant

- The service / repository / controller split and the
  `transaction.domain / repo / service / web / error` package layout.
- Putting the status-transition rules **inside the `TransactionStatus` enum** so
  they can be unit-tested without a Spring context (`TransactionStatusTest`).
- Keeping currency and amount limits in `application.yml`, bound to a validated
  `@ConfigurationProperties` record (`TransactionProperties`), so changing the
  variant is a one-line configuration change.
- Using a caller-supplied id as the JPA primary key, with `existsById` plus the
  database unique constraint as a backstop for duplicate detection.
- The single `ApiError` response shape and the mapping of each exception to a
  status code (400 format / 422 business rule / 404 / 409 / 500).
- Treating "Transaction Type" as the payment method (`CASH`, `CARD`, `UPI`,
  `ONLINE`), since the brief leaves the type values to the candidate.

## What was changed, corrected or rejected during development

These are visible in the commit history:

- **Transaction type semantics** (`b0757aa`) — the first version used generic
  types; changed to payment methods (`CASH/CARD/UPI/ONLINE`), which meant updating
  the enum, the validation message, the console, the tests and the docs together.
- **Permitted currencies** (`3a56122`) — added `INR` to the configured set and to
  the test that exercises every currency.
- **Amount-limit error message** (`29a477d`) — reformatted it to show a plain money
  value (e.g. `40000.00`) instead of a raw `BigDecimal` `toString`.
- **Web console "Recorded" receipt** (`3f77ef7`, then `392e02f`) — it showed a
  static `PENDING` line that stayed on screen after the status had been advanced,
  contradicting the table below it; reworked so the receipt tracks the live status.
- **Port** (`7809b0f`) — pinned to the default `8080`.
- **Business-rule failures now return `422`, not `400`** — unsupported currency and
  over-limit amount were originally mapped to `400` like a malformed request;
  changed to `422 Unprocessable Entity` so a client can tell a syntax error apart
  from a rule the server will not accept. Format-rule failures stay `400`.
- **Maximum amount raised to `40000.00`** (`application.yml`) and a boundary test
  added (an amount exactly on the limit is accepted).

## What the AI got wrong that had to be fixed

- **Catch-all exception handler turned 404s into 500s** (`9eebc6b`). The generic
  `Exception` handler in `GlobalExceptionHandler` was catching Spring's
  `NoResourceFoundException` (raised for, e.g., a browser requesting `/favicon.ico`)
  and returning `500` with an `ERROR` log line. Fixed by handling it explicitly as
  a plain `404`, adding a test (`returns404ForAnUnknownUrl`), and giving the console
  a data-URI favicon so it stops asking for one.
- **Stale UI confirmation** (see above, `3f77ef7`) — the AI's first console left the
  create confirmation showing `PENDING` forever.
- **Environment, not code:** the machine had no `JAVA_HOME`, so the first `mvnw`
  run failed until a JDK 17 was installed. No code change.

## How the final result was checked

- `./mvnw clean test` was run from a clean state — **35 tests, 0 failures, 0
  errors** (see [`TEST_OUTPUT.txt`](TEST_OUTPUT.txt)). The suite drives every
  operation over real HTTP against the in-memory database and asserts both the
  happy path and each failure case: validation failure, duplicate id, unknown id,
  forbidden status transition, unsupported currency, over-limit amount (and an
  amount exactly on the limit succeeding), unknown type, missing query parameter,
  and unknown URL.
- The build was also verified from a fresh `git clone` with no manual setup.
- The API was exercised by hand against a running instance (`./mvnw
  spring-boot:run`), checking that the status codes and JSON bodies match the
  README — including the duplicate-id case (`201` then `409`, original unchanged)
  and the not-found case (`404` with a JSON error body).

### Manual check commands

```bash
./mvnw spring-boot:run   # in another terminal

curl -i -X POST localhost:8080/api/transactions -H 'Content-Type: application/json' \
  -d '{"transactionId":"t1","customerId":"c1","amount":50.00,"currency":"GBP","type":"CASH"}'
curl -i localhost:8080/api/transactions/t1
curl -i -X PATCH localhost:8080/api/transactions/t1/status -H 'Content-Type: application/json' \
  -d '{"status":"COMPLETED"}'
curl -i 'localhost:8080/api/transactions?customerId=c1'
```
