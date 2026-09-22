package com.nahid.booking.bookings;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    long countByClassSessionIdAndStatus(Long classSessionId, BookingStatus status);

    boolean existsByUserIdAndClassSessionIdAndStatus(Long userId, Long classSessionId, BookingStatus status);

    List<Booking> findByUserIdOrderByIdDesc(Long userId);
}
