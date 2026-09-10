# Booking and Credit Ledger API

A small Java backend built in three stages to demonstrate booking transactions and, later, credit correctness under concurrent requests.

**Current stage: V1, basic bookings.** Java 21, Spring Boot 4.1.1, PostgreSQL 16, Flyway, Maven and Swagger UI.

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

Seed dates are relative to the **first database migration**, so existing seeded classes eventually become past sessions. Create a new future class through Swagger when that happens. No database reset is needed. V1 records credit cost but does not charge credits.

## API behaviour

| Operation | Result |
|---|---|
| List classes / demo users / a user's bookings, or view one class or booking | 200 |
| Create a future class / booking | 201 |
| Cancel an existing booking | 204; repeating cancellation preserves its original timestamp |
| Missing user, class or booking, or unknown endpoint | 404 |
| Duplicate active booking, full class, past or cancelled class | 409 |
| Invalid fields, invalid or non-numeric IDs, or malformed JSON body | 400 |
| Unsupported HTTP method / content type | 405 / 415 |

Errors, including framework errors, use `application/problem+json` with a status and readable detail. The `Location` header of a 201 response can be fetched with GET. Field validation errors include an `errors` array. Responses do not expose stack traces or database statements.

## Code structure

```text
src/main/java/com/nahid/booking/
  catalog/   Class sessions: entity, repository, service, DTOs and controller
  booking/   Booking creation, cancellation and history
  users/     Read-only demo users
  shared/    Error responses, UTC clock and OpenAPI configuration
```

Controllers accept and return record DTOs. Services own transactions and convert entities to responses before leaving the service layer. Flyway owns schema changes; Hibernate validates mappings instead of changing the database. The original baseline migration is preserved.

The booking service deliberately counts confirmed bookings and then inserts a booking without locking. This works for sequential requests but **can exceed capacity under concurrent requests**. The database's partial unique index still rejects two active bookings for the same user and class. V3 will reproduce and fix the capacity race; V1 does not claim concurrency safety.

Cancellation keeps the original booking and marks it cancelled. It frees capacity for a new booking. There is no cutoff fee or background completion process in V1.

## Tests

With Docker running:

```powershell
.\mvnw.cmd verify
```

On macOS/Linux use `./mvnw verify`.

Testcontainers starts a separate PostgreSQL 16 container, applies the real Flyway migration, and runs the API on a random HTTP port. Test fixtures are reset only in that disposable database; the Compose development database is not used. Docker must be available: tests fail rather than silently skip database checks.

The integration suite covers listing, validated creation, fetching created resources from their `Location` headers, booking, duplicate rejection, cancellation and rebooking, two-seat capacity, missing records, malformed requests, framework errors as problem details, past/cancelled sessions, database uniqueness enforcement, transaction rollback and Swagger/OpenAPI. GitHub Actions runs the same Maven verification on pushes and pull requests.

Local verification on 10 September 2026: **13 tests passed, 0 failures, 0 errors, 0 skipped**, against PostgreSQL 16, and a clean `verify` packaged the runnable JAR successfully. The workflow is configured but has not yet run on GitHub because these changes have not been pushed.

## Revised roadmap

- **V1:** Basic bookings, validation, cancellation, PostgreSQL integration tests, Swagger, README and CI.
- **V2:** Credit accounts and an append-only ledger; reserve credits on booking and refund cancellation. Check each transaction, account balances and atomic rollback.
- **V3:** Demonstrate concurrent booking/spending failures, then add consistent database locking and record measured before/after results.
- **Optional:** Idempotency, login and permissions, waitlists, scheduled settlement and hosting.

Build and explain one stage at a time. Test outcomes from V1 are not evidence that the unbuilt credit or concurrency features work.
