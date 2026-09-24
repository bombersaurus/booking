package com.nahid.booking.classes;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "class_sessions")
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Instant startsAt;
    private int capacity;

    protected ClassSession() {
    }

    public boolean hasStarted() {
        return !startsAt.isAfter(Instant.now());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getCapacity() {
        return capacity;
    }
}
