package com.nahid.booking.credits;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TopUpRequest(@NotNull @Positive @Max(100) Long amount) {}
