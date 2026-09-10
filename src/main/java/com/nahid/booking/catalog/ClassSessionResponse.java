package com.nahid.booking.catalog;

import java.time.Instant;

public record ClassSessionResponse(Long id, String name, Instant startsAt,
                                   int capacity, long creditCost, Instant cancelledAt) {
    static ClassSessionResponse from(ClassSession session) {
        return new ClassSessionResponse(session.getId(), session.getName(), session.getStartsAt(),
                session.getCapacity(), session.getCreditCost(), session.getCancelledAt());
    }
}

