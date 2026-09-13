# V3: concurrent requests

V3 reproduces the races left in V2, fixes them with consistent row locking and records measured results. See PLAN.md for the agreed scope.

## Method

`ConcurrencyIntegrationTest` releases many HTTP requests at the same instant behind a latch, then inspects the database. Each scenario repeats for 20 rounds against a freshly reset, disposable PostgreSQL 16 container, because a race does not appear on every attempt. After every round it also checks that each ledger transaction sums to zero and that every stored balance equals its ledger entries.

The race tests were written first and run against the unchanged V2 code (main at `338bc12`) to record the failures. The locking fix was then added and the same tests rerun.

Environment: one Windows laptop, Docker Desktop, Hikari's default pool of 10 connections. Latency is measured per HTTP request from the client. The numbers are from single runs on one machine, so treat them as indicative. Throughput varied between runs; the double spend scenario measured 331 requests per second in one run and 444 in the soak.

## Correctness

| Scenario | Before (V2, no locks) | After (V3) |
|---|---|---|
| 25 members book one 5 seat class at once | 20 of 20 rounds oversold, up to 13 bookings for 5 seats | 0 of 20; exactly 5 bookings every round |
| One member with 5 credits books 15 one credit classes at once | 20 of 20 rounds overspent: all 15 bookings accepted | 0 of 20; exactly 5 accepted, 10 refused with 409 |
| 10 cancellations of one booking at once | 180 of 200 requests failed with 500 (the unique index blocked a second refund) | 0 of 20; all 200 returned 204, one refund each |
| Stored balances compared with the ledger | Drifted in every booking round (lost updates on member and `RESERVED` accounts) | Always equal |

A longer soak of the locked code with `-Drace.rounds=100` found 0 violations in 100 rounds per scenario, about 6,000 concurrent requests in total, with no server errors.

## Latency and throughput

| Scenario | p50 ms, before / after | p95 ms, before / after | Requests per second, before / after |
|---|---|---|---|
| Capacity | 31.6 / 68.9 | 47.2 / 107.5 | 594 / 272 |
| Double spend | 28.2 / 37.4 | 36.4 / 47.4 | 415 / 331 |
| Double refund | 18.5 / 13.1 | 24.5 / 18.6 | 487 / 619 |

The "before" figures are for incorrect behaviour, so they are not a target. Bookings for one class now run one at a time, which roughly halves throughput in the capacity scenario. Cancellations got faster because duplicates now return early instead of failing and rolling back.

## The fix

Every write takes row locks (`SELECT ... FOR UPDATE`) in one fixed order:

1. The class row when booking, or the booking row when cancelling.
2. Credit accounts, one at a time, in ascending id order.

No path takes a class or booking lock after an account lock, and accounts are always locked in the same order. Two transactions can therefore wait for each other only in a line, never in a cycle, so they cannot deadlock.

- **Capacity:** the class lock makes the capacity count exact, because the next booking for that class waits until this one commits.
- **Double spend:** the balance is read after the member's account is locked, so no other transaction can spend it in between.
- **Double refund:** the booking lock makes later cancellations wait, then read `CANCELLED` and return without refunding.
- **Lost updates:** a balance is only changed while its row is locked.

One detail matters with JPA. Account ids are looked up with id only queries before locking. If an account entity were loaded first, the locking query would return that already loaded, stale copy, and the lost update would come back.

## Tradeoffs

- **Hot row.** Every booking and refund locks the single `RESERVED` account, so all credit movements queue on one row, even for different classes. It is held only for the ledger inserts and the commit. The alternatives are to stop storing a balance on system accounts and derive it from the ledger, to split `RESERVED` per class, or to apply system account changes as atomic increments.
- **Pessimistic rather than optimistic locking.** Optimistic version checks would make losing requests fail and retry. Under this kind of contention, where many requests aim at one seat pool, queueing is simpler and wastes no work.
- **Unbounded lock waits.** A request waits as long as the lock holder takes. A `lock_timeout` would turn a stuck lock into a quick error.
- **Scope.** The tests show these specific races no longer occur under the tested load. They are evidence, not proof.

## Definition of done

- [x] A reproducible race test, written and run before any locks.
- [x] Capacity protected under concurrent bookings.
- [x] Double spending prevented with a consistent lock order.
- [x] Duplicate refunds prevented, with no server errors.
- [x] Atomic booking and credit rollback, and ledger consistency, verified against real PostgreSQL.
- [x] Actual results recorded, with tradeoffs explained.
- [x] CI passes on the V3 pull request.

## Review

1. Why does locking the class row make `count` then `insert` safe?
2. Why must every path lock accounts in the same order?
3. Why would loading an account before locking it bring the lost update back?
4. What would change if `RESERVED` stopped storing a balance?
