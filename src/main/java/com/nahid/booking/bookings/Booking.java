package com.nahid.booking.bookings;

import jakarta.persistence.*;

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

    protected Booking() {
    }

    public Booking(Long userId, Long classSessionId) {
        this.userId = userId;
        this.classSessionId = classSessionId;
        this.status = BookingStatus.CONFIRMED;
    }

    public void cancel() {
        status = BookingStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getClassSessionId() {
        return classSessionId;
    }

    public BookingStatus getStatus() {
        return status;
    }
}
