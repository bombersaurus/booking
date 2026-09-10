package com.nahid.booking.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Classes", description = "Browse the schedule and add a class")
@RequestMapping("/api/v1/classes")
public class ClassSessionController {
    private final ClassSessionService service;

    public ClassSessionController(ClassSessionService service) { this.service = service; }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary = "View classes")
    public List<ClassSessionResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    @io.swagger.v3.oas.annotations.Operation(summary = "View a class")
    public ClassSessionResponse get(@PathVariable @Positive Long id) { return service.get(id); }

    @PostMapping
    @io.swagger.v3.oas.annotations.Operation(summary = "Create a class")
    public ResponseEntity<ClassSessionResponse> create(@Valid @RequestBody CreateClassSessionRequest request) {
        ClassSessionResponse result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/classes/" + result.id())).body(result);
    }
}
