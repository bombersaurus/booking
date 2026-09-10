package com.nahid.booking.credits;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByTransactionId(Long transactionId);

    @Query("""
            select new com.nahid.booking.credits.CreditEntryResponse(t.id, t.kind, e.amount, t.bookingId, e.createdAt)
            from LedgerEntry e join CreditTransaction t on t.id = e.transactionId
            where e.accountId = :accountId
            order by e.id desc
            """)
    List<CreditEntryResponse> statementFor(@Param("accountId") Long accountId);
}
