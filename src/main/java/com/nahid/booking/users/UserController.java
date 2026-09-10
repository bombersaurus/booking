package com.nahid.booking.users;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Members", description = "Find a demo member to book with")
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary = "View demo members")
    public List<UserResponse> list() { return service.list(); }
}
