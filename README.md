[![CI](https://github.com/bombersaurus/booking/actions/workflows/ci.yml/badge.svg)](https://github.com/bombersaurus/booking/actions/workflows/ci.yml)

# Booking API

A small Spring Boot API for booking places on fitness classes. The main thing it does well is not overbooking a class when lots of people try to book at the same time.

Java 21, Spring Boot, PostgreSQL, Flyway, Testcontainers, GitHub Actions and Swagger UI.

## The problem

To book a place, the service counts how many places are taken and then saves the booking. If two requests arrive at the same moment, both can count 4 of 5 places taken and both save a booking, so the class ends up with 6.

`ConcurrentBookingTest` reproduces this. It sends 25 booking requests for a 5 place class at the same instant, repeats that 10 times, and checks the class never ends up with more than 5 bookings.

## The fix

When booking, the service loads the class with `SELECT ... FOR UPDATE`. That locks the class row, so the next booking for the same class has to wait until the current one has finished. The count is then always correct. Bookings for different classes do not block each other.

As a second safety net, a partial unique index in PostgreSQL stops a user from having two confirmed bookings for the same class, even if the service check was somehow skipped.

To see the race yourself, change `findByIdForUpdate` back to `findById` in `BookingService` and run the test. It fails with the class overbooked.

## Endpoints

| Method | Path | What it does |
|---|---|---|
| GET | `/api/v1/classes` | List classes |
| POST | `/api/v1/bookings` | Book a place, body `{"userId": 1, "classSessionId": 1}` |
| DELETE | `/api/v1/bookings/{id}` | Cancel a booking |
| GET | `/api/v1/users/{userId}/bookings` | A user's bookings, newest first |

Errors come back as `application/problem+json`: 404 when a user, class or booking does not exist, 409 when a class is full, has started, or is already booked by that user, and 400 for invalid input.

Demo data: users 1 to 3 (Alice, Bob and Carol) and three classes. HIIT only has 2 places, so booking it as all three users shows the "class is full" error. The demo classes are dated from when the database was created, so if they have passed, reset the database (see below).

## Run it

You need Java 21 and Docker.

```bash
docker compose up -d
./mvnw spring-boot:run
```

On Windows use `.\mvnw.cmd spring-boot:run`. Then open http://localhost:8080/swagger-ui.html.

To reset the database: `docker compose down -v` then `docker compose up -d`.

There is no login. User IDs are passed in directly.

## Tests

```bash
./mvnw verify
```

The tests run against a real PostgreSQL in a Docker container (Testcontainers), using the same Flyway migration as the app. GitHub Actions runs them on every push.
