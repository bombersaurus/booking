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

    // Only called on accounts locked by CreditService.lockInIdOrder, so reading,
    // changing and writing back the balance cannot interleave with another transaction.
    void apply(long amount) { balance += amount; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public AccountKind getKind() { return kind; }
    public long getBalance() { return balance; }
}
