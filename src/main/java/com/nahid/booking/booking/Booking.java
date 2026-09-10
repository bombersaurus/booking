package com.nahid.booking.booking;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long classSessionId;
    @Enumerated(EnumType.STRING)
    private BookingStatus status;
    private Instant createdAt;
    private Instant cancelledAt;

    protected Booking() {}

    public Booking(Long userId, Long classSessionId, Instant createdAt) {
        this.userId = userId;
        this.classSessionId = classSessionId;
        this.createdAt = createdAt;
        this.status = BookingStatus.CONFIRMED;
    }

    public void cancel(Instant now) {
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getClassSessionId() { return classSessionId; }
    public BookingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}

