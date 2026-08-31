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

The latest test run is in [`TEST_OUTPUT.txt`](TEST_OUTPUT.txt): **73 tests, all passing.**

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
| Maximum amount | `40000.00` | `application.yml` -> `transaction.max-amount` |
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

Validation is in two layers. **Format** rules are Bean Validation annotations on the
request record and fail with **400** (the request is malformed). **Business** rules
need the database or the configured limits, are enforced in `TransactionService`,
and fail with **422** (the request is well formed but the server will not accept
it) — or **409** where it is a conflict with existing state.

| Field | Rule | Enforced by | On failure |
|---|---|---|---|
| `transactionId` | required, `^[A-Za-z0-9-]{1,64}$` | annotation | 400 |
| `transactionId` | must not already exist | service (`existsById`, plus DB unique constraint as backstop) | 409 |
| `customerId` | required, `^[A-Za-z0-9-]{1,64}$` | annotation | 400 |
| `amount` | required, `> 0`, at most 2 decimal places | annotation | 400 |
| `amount` | `<=` configured maximum (40000.00) | service | 422 |
| `currency` | required, exactly 3 uppercase letters | annotation | 400 |
| `currency` | must be in the permitted set | service | 422 |
| `type` | required, one of the `TransactionType` values | annotation + JSON parsing | 400 |
| `status` on create | not accepted; server sets `PENDING` | ignored by DTO | - |
| `status` on update | must be a valid enum value **and** an allowed transition | JSON parsing + service | 400 / 409 |
| `transactionId` / `customerId` in the URL | same `^[A-Za-z0-9-]{1,64}$` shape | `@Validated` controller | 400 |

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
`400` if a format rule fails, `422` if a business rule fails (unsupported currency,
amount over the limit), `409` if the id already exists.

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

Each rule is tested at the lowest layer it lives in, then integration tests prove
the layers are wired together. 73 tests in five classes:

| Class | Scope | Speed |
|---|---|---|
| **`TransactionStatusTest`** | the transition rules alone - parameterised over allowed pairs, forbidden pairs, self-transitions, terminal states. No Spring. | instant |
| **`CreateTransactionRequestValidationTest`** | the Bean Validation annotations alone, driven through a raw `Validator` - one test per constraint, proving each fires on exactly the input it should. No Spring. | instant |
| **`TransactionServiceTest`** | the business rules alone - mocked repository, **fixed `Clock`**. Currency whitelist, amount cap and its boundary, amount-scale normalisation, timestamps from the clock, duplicate rejected before any write, a DB unique-violation turned into a clean `409`, every status-update outcome. `verify(never()).save()` on each rejection path. No Spring context. | fast (mocked) |
| **`TransactionControllerTest`** | the web layer alone - `@WebMvcTest`, mocked service. Proves the controller and exception handler map results and exceptions to the right status and body (201 + `Location`, 400 vs 422 vs 409 vs 404), that a malformed body never reaches the service, and that a malformed id in the URL is a 400 rather than a wasted lookup. | fast (slice) |
| **`TransactionApiTest`** | whole stack - `@SpringBootTest` + `MockMvc` + real H2, nothing mocked. The four scenarios the brief names, all four operations end to end, the full status lifecycle, and that data written by one request is really there for the next. | slower (full context) |

The provided `contextLoads` smoke test is kept. Every integration test clears the
table first, so order does not matter.

## Known limitations

- No authentication or authorisation.
- No paging on the per-customer list - fine for the exercise, not for real volumes.
- Duplicate-id detection is check-then-write; the database unique constraint is the
  real guard against a race, and the service maps that back to a `409`.
- The assigned variant was not applied (see Assumptions).
- Currency is only shape-checked against the permitted set, not against a real
  ISO-4217 register, so an unassigned but well-formed code would still be rejected
  for the right reason but with a generic message.

## What I would do with more time

- Apply the real assigned variant.
- Add per-field detail to the 422 business-rule errors (currency, amount) so the
  response body points at the offending field, the way the 400 field errors do.
- Idempotency on create (same id + same body -> return the existing resource).
- OpenAPI/Swagger for the contract.
- Persist a small status-change history rather than only the current status.

## AI assistance

AI was used. See [`AI_USAGE.md`](AI_USAGE.md).
