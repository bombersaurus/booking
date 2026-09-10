package com.nahid.booking.catalog;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateClassSessionRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Future Instant startsAt,
        @NotNull @Positive Integer capacity,
        @NotNull @Positive Long creditCost) {}

