package com.nahid.booking.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Bookings", description = "Reserve, review and cancel a place")
@RequestMapping("/api/v1")
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) { this.service = service; }

    @PostMapping("/bookings")
    @io.swagger.v3.oas.annotations.Operation(summary = "Book a place")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + result.id())).body(result);
    }

    @GetMapping("/bookings/{id}")
    @io.swagger.v3.oas.annotations.Operation(summary = "View a booking")
    public BookingResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @DeleteMapping("/bookings/{id}")
    @io.swagger.v3.oas.annotations.Operation(summary = "Cancel a booking")
    public ResponseEntity<Void> cancel(@PathVariable @Positive Long id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/bookings")
    @io.swagger.v3.oas.annotations.Operation(summary = "View booking history")
    public List<BookingResponse> list(@PathVariable @Positive Long userId) {
        return service.listForUser(userId);
    }
}
