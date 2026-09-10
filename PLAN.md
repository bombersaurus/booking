# Latest agreed plan

Source: the September 5 discussion in “Check ledger project access”, superseding the original five-version architecture.

## V1: basic bookings

List and create classes; book, cancel and rebook; clear errors for full classes, duplicates and invalid input. Demonstrate through Swagger. Include database integration tests, README and GitHub test automation.

Use seeded demo users without authentication. Capacity is checked naively; no claim of concurrency safety. No ledger, waitlist, idempotency, login or scheduled processing.

## V2: credits

Add credit accounts and immutable credit history. Reserve credits when booking and refund them on cancellation. Verify each transaction balances, the right account is affected, insufficient credits are refused and failures leave no partial updates.

## V3: concurrent requests

Write a reproducible race test before adding locks. Then protect capacity and prevent double-spending with consistent lock ordering. Verify duplicate prevention and atomic booking/credit rollback against real PostgreSQL. Record actual results and explain the trade-offs.

## Optional extensions

Safe retries, login/permissions, waiting lists, automatic settlement and a hosted demo. These are not required to finish the core project.

## Working copy

The active copy is now in the Ledger workspace's `booking` folder. It was copied from the September 4 project with Git history preserved; the original folder was left untouched.

