package com.nahid.booking.bookings;

public record BookingResponse(Long id, Long userId, Long classSessionId, BookingStatus status) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getUserId(), booking.getClassSessionId(), booking.getStatus());
    }
}
