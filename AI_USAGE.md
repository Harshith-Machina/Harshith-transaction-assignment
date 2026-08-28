# AI Usage Disclosure

> **Note to the candidate:** this is a draft based on how the code was produced.
> Read every file, then edit the sections marked _[confirm / add your own words]_
> so this reflects **your** review. Do not submit claims you cannot back up in the
> interview.

## Tools used

- **Claude (Anthropic), via the Claude Code CLI** - used as a pair programmer to
  go from the challenge document to a working implementation.

## What it was used for

- Reading the challenge brief and the starter project and proposing a structure
  (package layout, layering, where each rule belongs) before any code was written.
- Generating the first version of: the `Transaction` entity and the two enums, the
  repository, `TransactionService`, the controller, the request/response DTOs, the
  exception types and the `@RestControllerAdvice`, the configuration properties,
  and both test classes.
- Drafting this README and this disclosure.

## What the AI generated or suggested that is significant

- The service/repository/controller split and the `transaction.domain / repo /
  service / web / error` package layout.
- Putting the status-transition rules **inside the `TransactionStatus` enum** so
  they can be unit-tested without Spring.
- Keeping currency and amount limits in `application.yml` (bound to a validated
  `@ConfigurationProperties` record) so the "variant" is a one-line change.
- Using a caller-supplied id as the JPA primary key, with `existsById` plus the
  database unique constraint as a backstop for duplicate detection.
- The single `ApiError` response shape and the mapping of each exception to a
  status code (400 / 404 / 409 / 500).

## What was changed, corrected or rejected _[confirm / add your own words]_

- _[e.g. "I renamed X", "I removed a test that asserted nothing", "I changed the
  status model because my variant defines different types", "I decided a
  same-status update should be a 200 no-op instead of a 409" - fill in what you
  actually did.]_

## What the AI got wrong that had to be fixed _[confirm / add your own words]_

- The environment had no `JAVA_HOME` set, so the first `mvnw` run failed; this was
  an environment issue, not the code. _[Add anything you hit: a test that failed
  first time, an annotation that did not behave as expected, etc. If nothing else
  broke, say so honestly.]_

## How the final result was checked

- `./mvnw clean test` was run from a clean state - **32 tests pass, 0 failures**
  (see `TEST_OUTPUT.txt`). The suite drives every operation over real HTTP against
  the in-memory database, and asserts both the happy path and each failure case
  (validation, duplicate id, unknown id, forbidden status transition, unsupported
  currency, over-limit amount).
- _[confirm]_ I also exercised the API by hand with _[curl / Postman / the `run`
  described below]_ and checked the status codes and bodies match the README.

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
