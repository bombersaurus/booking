package com.nahid.booking.booking;

import java.time.Instant;

public record BookingResponse(Long id, Long userId, Long classSessionId,
                              BookingStatus status, Instant createdAt, Instant cancelledAt) {
    static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getUserId(), booking.getClassSessionId(),
                booking.getStatus(), booking.getCreatedAt(), booking.getCancelledAt());
    }
}

