# V1: basic bookings

V1 is implemented. See README.md for startup and Swagger instructions and PLAN.md for the agreed three-stage scope.

The project uses Java 21 and Spring Boot 4.1.1 with springdoc 3.1.1. The old Spring Boot 3 / springdoc 2 setup instructions no longer apply.

## Implemented

- ClassSession entity, repository, service, record DTOs and class-listing controller.
- Validated class creation.
- Booking creation with a basic capacity check and active-booking uniqueness.
- Cancellation with a timestamp, repeated cancellation and rebooking.
- ProblemDetail responses for invalid input, missing records, duplicates and full classes, and for framework errors such as unknown endpoints, non-numeric IDs and unsupported methods.
- GET by ID for classes and bookings, so every 201 `Location` header can be fetched.
- Timestamps kept at PostgreSQL's microsecond precision, so create responses match what is read back.
- Read-only demo-user listing and booking history.
- PostgreSQL integration tests, Swagger, README and a GitHub Actions workflow.

The original Flyway baseline is unchanged. A second migration adds Carol as a third demo user, so the two-seat / three-user scenario can be demonstrated entirely through Swagger. Migration V2 is a schema sequence number, not the credit-ledger product version.

## Definition of done

- [x] List and create sessions.
- [x] Book as an existing demo user.
- [x] Reject duplicate active bookings with 409.
- [x] Cancel and rebook.
- [x] Refuse a third booking for a two-seat session under sequential requests.
- [x] Run automated integration checks against PostgreSQL.
- [x] Document the demo and configure CI.
- [ ] Run the workflow on GitHub after publishing the changes.

V1 does not include credits, locking, authentication, idempotency, waitlists or scheduled settlement. The capacity count followed by insertion is intentionally vulnerable to concurrent requests; V3 will reproduce and fix that race.

## Review before V2

1. What does the service's @Transactional method do, and when does it commit?
2. How does Spring supply a service to its controller without the controller constructing it?
3. Why does Hibernate validate the schema while Flyway changes it?
4. How can two concurrent requests both see one remaining seat and both create a booking?
5. Why does a cancelled booking remain in the database?

Understand the working code and commit the reviewed V1 checkpoint before adding credit movements.
