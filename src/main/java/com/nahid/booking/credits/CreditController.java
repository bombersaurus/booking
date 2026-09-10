package com.nahid.booking.credits;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Credits", description = "Check a balance and add credits")
@RequestMapping("/api/v1/users/{userId}/credits")
public class CreditController {
    private final CreditService service;

    public CreditController(CreditService service) { this.service = service; }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary = "View credits")
    public CreditStatementResponse statement(@PathVariable @Positive Long userId) {
        return service.statement(userId);
    }

    @PostMapping
    @io.swagger.v3.oas.annotations.Operation(summary = "Add credits")
    public CreditStatementResponse topUp(@PathVariable @Positive Long userId,
                                         @Valid @RequestBody TopUpRequest request) {
        return service.topUp(userId, request.amount());
    }
}
