package com.nahid.booking.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBookingRequest(@NotNull @Positive Long userId,
                                   @NotNull @Positive Long classSessionId) {}

