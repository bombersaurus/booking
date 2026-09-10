package com.nahid.booking.credits;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long transactionId;
    private Long accountId;
    private long amount;
    private Instant createdAt;

    protected LedgerEntry() {}

    LedgerEntry(Long transactionId, Long accountId, long amount, Instant createdAt) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getTransactionId() { return transactionId; }
    public Long getAccountId() { return accountId; }
    public long getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
