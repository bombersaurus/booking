package com.nahid.booking.credits;

import java.time.Instant;

/** One line of a member's statement. A negative amount left the member's account. */
public record CreditEntryResponse(Long transactionId, TransactionKind kind, long amount,
                                  Long bookingId, Instant createdAt) {}
