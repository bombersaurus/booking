# V2: credits

V2 adds credit accounts and an append only, double entry ledger. See README.md for the Swagger demo and ledger model, and PLAN.md for the agreed scope.

## Decisions

- Every member starts with 5 credits, granted by migration as real ledger transactions.
- `POST /api/v1/users/{userId}/credits` adds 1 to 100 credits, so running out is easy to demonstrate.
- Booking reserves the class's credit cost. Cancelling refunds the reservation in full, at any time.
- Bookings made before V2 have no reservation, so cancelling them refunds nothing.
- Append only history is enforced by triggers rather than a second database role, so it also binds the table owner.
- Migration `V3__credit_ledger.sql` is a schema sequence number, not the V3 product stage.

## Definition of done

- [x] Credit accounts and immutable credit history.
- [x] Reserve credits on booking and refund them on cancellation.
- [x] Every transaction balances: a deferred trigger enforces it and every test checks it.
- [x] The right accounts are affected, and other members are untouched.
- [x] Insufficient credits are refused with 409.
- [x] Failures leave no partial updates.
- [x] CI passes on the V2 pull request.

## Still not included

No locking. Balances are read, changed in memory and written back, so concurrent requests can lose an update or overspend, and capacity can still be exceeded. V3 reproduces this and adds consistent row locking. There is no authentication, idempotency, waitlist or settlement.

## Review before V3

1. Why must a booking and its reservation share one database transaction?
2. Why is the balance rule checked at commit instead of after each insert?
3. What does storing a balance on the account buy you, and what keeps it honest?
4. How could two concurrent bookings by one member spend the same credits?
