package com.nahid.booking.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    long countByClassSessionIdAndStatus(Long classSessionId, BookingStatus status);
    boolean existsByUserIdAndClassSessionIdAndStatusNot(Long userId, Long classSessionId, BookingStatus status);
    List<Booking> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    // SELECT ... FOR UPDATE: concurrent cancellations of one booking queue on this row.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
}
