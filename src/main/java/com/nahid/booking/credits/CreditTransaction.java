package com.nahid.booking.credits;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "credit_transactions")
public class CreditTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private TransactionKind kind;
    private Long bookingId;
    private Instant createdAt;

    protected CreditTransaction() {}

    CreditTransaction(TransactionKind kind, Long bookingId, Instant createdAt) {
        this.kind = kind;
        this.bookingId = bookingId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public TransactionKind getKind() { return kind; }
    public Long getBookingId() { return bookingId; }
    public Instant getCreatedAt() { return createdAt; }
}
