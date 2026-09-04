# v1 — make it work

**Goal:** create a session and book it, through Swagger, against real Postgres.

**Not in v1:** ledger, locking, idempotency, waitlist, JWT, the scheduled job. Resist all of them. v1 exists so that when you add locking in v3 you're changing code you understand.

---

## Setup

**1. Generate the project** at [start.spring.io](https://start.spring.io):

- Maven, Java 21, Spring Boot 3.3.x
- Group `com.yourname`, Artifact `booking`
- Dependencies: **Spring Web**, **Spring Data JPA**, **PostgreSQL Driver**, **Flyway Migration**, **Validation**

Then add springdoc to `pom.xml` manually (it isn't on start.spring.io):

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

**2. Drop in the files from this folder**, overwriting the generated `application.properties` (delete it — you're using `application.yml`).

**3. Start the database:** `docker compose up -d`

**4. Run the app.** It should start, Flyway should apply `V1__baseline.sql`, and Hibernate should validate cleanly. It won't yet, because you have no entities — that's step one of the actual work.

Check the schema landed: `docker exec -it booking-db psql -U booking -d booking -c '\dt'`

---

## What you write

Four packages, and roughly this order. Get each one running before starting the next.

**1. `catalog` — read-only first.**
`ClassSession` entity, `ClassSessionRepository extends JpaRepository`, a controller with `GET /api/v1/classes`. Return a record DTO, not the entity.

Stop here and confirm the seeded sessions come back through Swagger. That's your whole stack proven end to end.

**2. `catalog` — writes.** `POST /api/v1/classes` with `@Valid` on the request record. No auth yet, anyone can create one.

**3. `booking` — the naive version.** `Booking` entity, repository, and a service method that:

- loads the session
- counts existing `CONFIRMED` bookings
- if count < capacity, saves a `CONFIRMED` booking; otherwise returns an error

`@Transactional` on the service method, not the controller. This is deliberately racy. Don't fix it. v3 is where you break it on purpose and watch it fail.

**4. `booking` — cancellation.** `DELETE /api/v1/bookings/{id}` sets status `CANCELLED` and stamps `cancelled_at`.

**5. `shared` — error handling.** One `@RestControllerAdvice` mapping your domain exceptions to `ProblemDetail`. Session not found → 404. Session full → 409. Validation failure → 400. No stack traces in responses.

---

## Definition of done

Through Swagger, without touching the database directly:

- List sessions
- Create a session
- Book it as a user
- Book it again as the same user → the unique index rejects it, and your error handler turns that into a clean 409 rather than a 500
- Cancel, then rebook successfully
- Fill a 2-seat session and get a sensible refusal on the third booking

Commit after each of the five steps above. A commit history that shows the thing being built in stages is worth having when someone asks whether you wrote it.

---

## Before you move to v2

Close the laptop and answer these out loud. If any of them is fuzzy, that's the gap — reread that bit rather than pushing on.

1. What does `@Transactional` on your booking service method actually do? When does the transaction start and when does it commit?
2. You never call `new BookingService(...)`. So how does the controller get one?
3. `ddl-auto: validate` — what would have gone wrong if it were `update`?
4. Your booking method reads a count and then writes a row. Describe, concretely, how two simultaneous requests can put 3 people in a 2-seat class.

Question 4 is the whole project. If you can describe the failure precisely before you've seen it, v3 will go quickly.
