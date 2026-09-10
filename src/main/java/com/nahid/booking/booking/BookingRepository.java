package com.nahid.booking.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    long countByClassSessionIdAndStatus(Long classSessionId, BookingStatus status);
    boolean existsByUserIdAndClassSessionIdAndStatusNot(Long userId, Long classSessionId, BookingStatus status);
    List<Booking> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}

