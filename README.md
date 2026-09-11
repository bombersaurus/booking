# Booking and Credit Ledger API

A small Java backend built in three stages to demonstrate booking transactions and credit correctness under concurrent requests.

**Current stage: V3, concurrency.** Java 21, Spring Boot 4.1.1, PostgreSQL 16, Flyway, Maven and Swagger UI.

## Results at a glance

Requests released at the same instant, 20 rounds per scenario, against real PostgreSQL. "Before" is the unlocked V2 code and "after" is V3's ordered row locking. Method, latency figures and trade-offs are in [BUILD-V3.md](BUILD-V3.md).

| Scenario | Before | After |
|---|---|---|
| 25 members book one 5 seat class | Oversold in 20 of 20 rounds, up to 13 bookings | Exactly 5 bookings in every round |
| One member with 5 credits books 15 classes | Overspent in 20 of 20 rounds, all 15 accepted | Exactly 5 accepted in every round |
| 10 cancellations of one booking | 180 of 200 requests failed with 500 | All 204, one refund |
| Stored balances compared with the ledger | Lost updates in every booking round | Always equal |

A 100 round soak of the locked code, about 6,000 concurrent requests, found no violations and no server errors. Correctness costs throughput: in the capacity scenario, median latency rises from 31.6 ms to 68.9 ms because bookings for one class now run one at a time.

## Run locally

Install Java 21 and start Docker Desktop. Open this folder (the one containing `pom.xml`) in IntelliJ.

1. Run `docker compose up -d` in the project terminal.
2. Run `.\mvnw.cmd spring-boot:run` on Windows, or `./mvnw spring-boot:run` on macOS/Linux. Alternatively, run `BookingApplication` in IntelliJ.
3. Open [Swagger UI](http://localhost:8080/swagger-ui.html).
4. Stop the Java application with Ctrl+C (or IntelliJ's stop button). Use `docker compose stop` to stop PostgreSQL while preserving its data.

The application binds to localhost by default. This is an unauthenticated local demo: user IDs are supplied directly, anyone with access can create classes and cancel bookings, and there is no ownership enforcement.

Database settings can be overridden with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`. `SERVER_PORT` defaults to 8080; `SERVER_ADDRESS` defaults to 127.0.0.1. The Compose database uses local development credentials and port 5432.

## Demonstrate V1 in Swagger

Use **Try it out**, then **Execute** on each operation.

1. `GET /api/v1/users`: find Alice, Bob and Carol's IDs. Carol also lets you demonstrate three requests against a two-seat class.
2. `GET /api/v1/classes`: see Spin, Yoga and HIIT.
3. `POST /api/v1/classes`: create a future session with capacity 1. For example, use the body below, replacing the date with a future UTC date.
4. `POST /api/v1/bookings`: supply Alice's user ID and the new class ID. Expect **201** and a confirmed booking.
5. Repeat Alice's request: expect **409** for a duplicate booking.
6. Book as Bob: expect **409** because the class is full.
7. `DELETE /api/v1/bookings/{id}`: cancel Alice's booking. Expect **204**.
8. Book as Bob: expect **201**. Cancel Bob, then rebook as Alice to demonstrate cancellation and rebooking.
9. `GET /api/v1/users/{userId}/bookings`: inspect confirmed and cancelled history.
10. `GET /api/v1/users/{userId}/credits`: every member starts with 5 credits from a `GRANT`. Alice's statement shows each booking as a `RESERVATION` of the class's credit cost and each cancellation as a `REFUND`.
11. Create a class with `creditCost` 6 and book it as Carol: expect **409** because she has only 5 credits. Her balance and booking history are unchanged.
12. `POST /api/v1/users/{userId}/credits` with `{"amount": 1}`: Carol now has 6. Book again: expect **201** and a balance of 0.

Create-class example:

```json
{
  "name": "Evening Pilates",
  "startsAt": "2027-01-15T18:00:00Z",
  "capacity": 1,
  "creditCost": 2
}
```

Book example (replace both IDs with the returned values):

```json
{
  "userId": 1,
  "classSessionId": 4
}
```

Seed dates are relative to the **first database migration**, so existing seeded classes eventually become past sessions. Create a new future class through Swagger when that happens. No database reset is needed.

Starting V2 applies migration `V3__credit_ledger.sql` to the development database. It adds the credit tables and grants each existing member 5 credits. Bookings made before V2 have no reservation, so cancelling them refunds nothing. (Flyway numbers migrations separately from the product stages.)

## API behaviour

| Operation | Result |
|---|---|
| List classes / demo users / a user's bookings, or view one class or booking | 200 |
| View a member's credits, or add 1 to 100 credits | 200 |
| Create a future class / booking | 201 |
| Cancel an existing booking | 204; repeating cancellation preserves its original timestamp |
| Missing user, class or booking, or unknown endpoint | 404 |
| Duplicate active booking, full class, past or cancelled class, or not enough credits | 409 |
| Invalid fields, invalid or non-numeric IDs, or malformed JSON body | 400 |
| Unsupported HTTP method / content type | 405 / 415 |

Errors, including framework errors, use `application/problem+json` with a status and readable detail. The `Location` header of a 201 response can be fetched with GET. Field validation errors include an `errors` array. Responses do not expose stack traces or database statements.

## Code structure

```text
src/main/java/com/nahid/booking/
  catalog/   Class sessions: entity, repository, service, DTOs and controller
  booking/   Booking creation, cancellation and history
  credits/   Credit accounts, ledger transactions and entries, statements and top ups
  users/     Read-only demo users
  shared/    Error responses, UTC clock and OpenAPI configuration
```

Controllers accept and return record DTOs. Services own transactions and convert entities to responses before leaving the service layer. Flyway owns schema changes; Hibernate validates mappings instead of changing the database. The original baseline migration is preserved.

Every write takes row locks (`SELECT ... FOR UPDATE`) in one fixed order: first the class when booking, or the booking when cancelling, then credit accounts one at a time in ascending id order. Locking the class makes the capacity count exact. Locking an account before reading its balance prevents double spending and lost updates. Locking the booking makes duplicate cancellations wait and then return without a second refund. Because every path locks in the same order, transactions queue instead of deadlocking. The database's partial unique index still rejects two active bookings for the same user and class as a final safeguard.

Cancellation keeps the original booking and marks it cancelled. It frees capacity and refunds the reserved credits in full. There is no cutoff fee or background completion process.

## Credit ledger

Credits move between accounts in balanced transactions. Each transaction has ledger entries that sum to zero:

| Transaction | From | To |
|---|---|---|
| `GRANT` (seed) or `TOP_UP` | `ISSUED` | member |
| `RESERVATION` (booking) | member | `RESERVED` |
| `REFUND` (cancellation) | `RESERVED` | member |

`ISSUED` is the source of all credits, so it is the only account with a negative balance. Each account also stores a running `balance`, updated in the same database transaction as its entries.

PostgreSQL enforces the rules even if application code is wrong. Triggers reject any update or delete of ledger rows, and a deferred trigger rejects, at commit, any transaction whose entries do not sum to zero. Member and reserved balances cannot go below zero, and each booking can have at most one reservation and one refund. A booking and its reservation share one database transaction, so a failure leaves neither behind.

## Tests

With Docker running:

```powershell
.\mvnw.cmd verify
```

On macOS/Linux use `./mvnw verify`.

Testcontainers starts a separate PostgreSQL 16 container, applies the real Flyway migration, and runs the API on a random HTTP port. Test fixtures are reset only in that disposable database; the Compose development database is not used. Docker must be available: tests fail rather than silently skip database checks.

The integration suite covers listing, validated creation, fetching created resources from their `Location` headers, booking, duplicate rejection, cancellation and rebooking, two-seat capacity, missing records, malformed requests, framework errors as problem details, past/cancelled sessions, database uniqueness enforcement, transaction rollback and Swagger/OpenAPI. For credits it covers reservations and refunds, insufficient credits with full rollback, top ups and their validation, bookings made before credits existed, and append only and balanced ledger enforcement. After every test it checks that each transaction sums to zero and each stored balance equals its ledger entries.

`ConcurrencyIntegrationTest` covers the three races above and prints a summary per scenario (lines starting with `[race]`). To run only those, or a longer soak:

```powershell
.\mvnw.cmd test -Dtest=ConcurrencyIntegrationTest "-Drace.rounds=100"
``` GitHub Actions runs the same Maven verification on pushes and pull requests.

Local verification of V3 on 10 September 2026: **22 tests passed, 0 failures, 0 errors, 0 skipped**, against PostgreSQL 16. V1 and V2 also passed on GitHub Actions in pull requests #1 and #2.

## Revised roadmap

- **V1:** Basic bookings, validation, cancellation, PostgreSQL integration tests, Swagger, README and CI.
- **V2:** Credit accounts and an append-only ledger; reserve credits on booking and refund cancellation. Check each transaction, account balances and atomic rollback.
- **V3:** Demonstrate concurrent booking/spending failures, then add consistent database locking and record measured before/after results.
- **Optional:** Idempotency, login and permissions, waitlists, scheduled settlement and hosting.

Build and explain one stage at a time. The race tests show that these specific races no longer occur under the tested load; they are evidence, not proof.
