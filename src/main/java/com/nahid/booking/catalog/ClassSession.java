package com.nahid.booking.catalog;

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
    private long creditCost;
    private Instant cancelledAt;

    protected ClassSession() {}

    public ClassSession(String name, Instant startsAt, int capacity, long creditCost) {
        this.name = name;
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.creditCost = creditCost;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Instant getStartsAt() { return startsAt; }
    public int getCapacity() { return capacity; }
    public long getCreditCost() { return creditCost; }
    public Instant getCancelledAt() { return cancelledAt; }
}

