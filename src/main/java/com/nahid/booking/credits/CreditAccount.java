package com.nahid.booking.credits;

import jakarta.persistence.*;

@Entity
@Table(name = "credit_accounts")
public class CreditAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private AccountKind kind;
    private long balance;

    protected CreditAccount() {}

    // V2 deliberately reads the balance, changes it in memory and writes it back.
    // Concurrent requests can overwrite each other's update; V3 adds row locks.
    void apply(long amount) { balance += amount; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public AccountKind getKind() { return kind; }
    public long getBalance() { return balance; }
}
