# Customer Transactions Service

A small transaction-processing service built inside the Toucan starter project.
It implements the four required operations: create a transaction, get one by id,
update a transaction's status, and list a customer's transactions.

## Run it

Windows:

```bat
mvnw.cmd clean test
```

Linux / macOS:

```bash
./mvnw clean test
```

To run the app: `./mvnw spring-boot:run` (starts on `http://localhost:8080`).
The database is in-memory H2 and is recreated on every start.

### Optional web console

Opening `http://localhost:8080/` in a browser serves a small static page
(`src/main/resources/static/`) for recording a transaction, looking transactions
up by customer or id, and advancing status. It is a thin client over the same
REST API and is **not part of the assessed exercise** &mdash; the API is the
deliverable. It adds no dependencies and does not affect `mvnw clean test`.

The latest test run is in [`TEST_OUTPUT.txt`](TEST_OUTPUT.txt): **32 tests, all passing.**

## My understanding of the problem

Build the business logic for a transaction service: accept transactions, validate
them deliberately, store them, let their status change under controlled rules, and
return clear responses (including clear errors) for every case. The framework
plumbing is already done; the work is the domain logic, validation, error handling
and tests.

## Assumptions

The exercise assigns each candidate a "variant" (currencies, maximum amount,
transaction types, or one extra rule) in the invitation email. **That variant was
not available to me**, so I used the following defaults. They are all in one place
(`application.yml` for currency/amount, the `TransactionType` enum for types) and
are trivial to change:

| Setting | Value used | Where to change |
|---|---|---|
| Permitted currencies | `GBP`, `EUR`, `USD`, `INR` | `application.yml` -> `transaction.allowed-currencies` |
| Maximum amount | `10000.00` | `application.yml` -> `transaction.max-amount` |
| Transaction types | `CASH`, `CARD`, `UPI`, `ONLINE` | `TransactionType` enum |

The brief leaves "Transaction Type" open. For a shop-counter service the useful
meaning is **how the customer paid**, so the type values are payment methods.

Other assumptions:

- **Transaction ID is caller-supplied** and is the primary key. The requirement to
  reject an id that already exists only makes sense for a natural key.
- **New transactions always start as `PENDING`.** The client cannot set the initial
  status; "initial status" is a server rule, not caller input.
- **Amount** is stored as `BigDecimal` with scale 2. `10` and `10.00` are the same
  amount and are returned as `10.00`.
- **Currency** is an ISO-4217-style 3-letter uppercase code; only codes in the
  permitted set are accepted.
- Listing a customer with no transactions returns `200` and an empty array, not a
  `404`.
- No authentication, no paging, single-node. In scope for a week-long exercise: no.

## Validation rules

Format rules are Bean Validation annotations on the request record; rules that need
the database or configured limits are enforced in `TransactionService`.

| Field | Rule | Enforced by | On failure |
|---|---|---|---|
| `transactionId` | required, `^[A-Za-z0-9-]{1,64}$` | annotation | 400 |
| `transactionId` | must not already exist | service (`existsById`, plus DB unique constraint as backstop) | 409 |
| `customerId` | required, `^[A-Za-z0-9-]{1,64}$` | annotation | 400 |
| `amount` | required, `> 0`, at most 2 decimal places | annotation | 400 |
| `amount` | `<=` configured maximum (10000.00) | service | 400 |
| `currency` | required, exactly 3 uppercase letters | annotation | 400 |
| `currency` | must be in the permitted set | service | 400 |
| `type` | required, one of the `TransactionType` values | annotation + JSON parsing | 400 |
| `status` on create | not accepted; server sets `PENDING` | ignored by DTO | - |
| `status` on update | must be a valid enum value **and** an allowed transition | JSON parsing + service | 400 / 409 |

### Status transition rules

```
PENDING   -> COMPLETED | FAILED | CANCELLED
COMPLETED -> REVERSED
FAILED    -> (terminal)
CANCELLED -> (terminal)
REVERSED  -> (terminal)
```

Reasoning: a transaction stays `PENDING` until it settles; from there it succeeds
(`COMPLETED`), fails (`FAILED`), or is pulled before settling (`CANCELLED`). Only a
settled transaction can be `REVERSED`. `FAILED`, `CANCELLED` and `REVERSED` are end
states. A "transition" to the same status is rejected (409) - nothing would change.
The rules live in the `TransactionStatus` enum so they are unit-testable on their own.

## API

Base path `/api/transactions`. All bodies are JSON. Errors share one shape
(`timestamp`, `status`, `error`, `message`, `path`, and `fieldErrors` for
validation failures).

### A. Create - `POST /api/transactions`

```json
{ "transactionId": "txn-1001", "customerId": "cust-42",
  "amount": 125.50, "currency": "GBP", "type": "CASH" }
```

`201 Created`, `Location: /api/transactions/txn-1001`, body is the stored
transaction with `status: "PENDING"`.
`400` if validation fails, `409` if the id already exists.

### B. Get one - `GET /api/transactions/{transactionId}`

`200` with the transaction, or `404`.

### C. Update status - `PATCH /api/transactions/{transactionId}/status`

```json
{ "status": "COMPLETED" }
```

`200` with the updated transaction. `404` if unknown, `409` if the transition is
not allowed, `400` if the status value is not a known enum.

### D. List a customer's transactions - `GET /api/transactions?customerId={customerId}`

`200` with a JSON array (oldest first), empty if the customer has none.
`400` if `customerId` is missing.

## How I approached testing

- **`TransactionStatusTest`** - plain unit tests for the transition rules
  (parameterised: allowed pairs, forbidden pairs, self-transitions, terminal states).
- **`TransactionApiTest`** - `@SpringBootTest` + `MockMvc` against the real H2
  database, driving each operation over HTTP. Covers the four cases the brief names
  (created OK, rejected by validation, duplicate id, unknown id) plus unsupported
  currency, over-limit amount, unknown type, get existing, both status-update
  outcomes, unknown-id status update, per-customer filtering, empty list, and the
  missing-parameter case.
- The provided `contextLoads` sample test is kept.
- Each API test clears the table first, so tests do not depend on order.

## Known limitations

- No authentication or authorisation.
- No paging on the per-customer list - fine for the exercise, not for real volumes.
- Duplicate-id detection is check-then-write; the database unique constraint is the
  real guard against a race, and the service maps that back to a `409`.
- The assigned variant was not applied (see Assumptions).
- `Clock` is injectable but time is not asserted precisely in the integration tests.

## What I would do with more time

- Apply the real variant and add a custom `@SupportedCurrency` validation
  annotation so the currency check reports as a field error like the others.
- A `TransactionService` unit test with a mocked repository and a fixed `Clock`,
  separate from the HTTP tests.
- Idempotency on create (same id + same body -> return the existing resource).
- OpenAPI/Swagger for the contract.
- Persist a small status-change history rather than only the current status.

## AI assistance

AI was used. See [`AI_USAGE.md`](AI_USAGE.md).
