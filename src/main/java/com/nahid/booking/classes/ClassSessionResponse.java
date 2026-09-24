package com.nahid.booking.classes;

import java.time.Instant;

public record ClassSessionResponse(Long id, String name, Instant startsAt, int capacity) {

    public static ClassSessionResponse from(ClassSession session) {
        return new ClassSessionResponse(session.getId(), session.getName(), session.getStartsAt(), session.getCapacity());
    }
}
