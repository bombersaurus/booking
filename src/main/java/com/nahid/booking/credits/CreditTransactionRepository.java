package com.nahid.booking.credits;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {
    Optional<CreditTransaction> findByBookingIdAndKind(Long bookingId, TransactionKind kind);
    boolean existsByBookingIdAndKind(Long bookingId, TransactionKind kind);
}
