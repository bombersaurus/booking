package com.nahid.booking.users;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private Instant createdAt;

    protected User() {}

    public Long getId() { return id; }
    public String getEmail() { return email; }
}

