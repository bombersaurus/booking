package com.nahid.booking.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
    List<ClassSession> findAllByOrderByStartsAtAscIdAsc();

    // SELECT ... FOR UPDATE: concurrent bookings for one class queue on this row.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ClassSession s where s.id = :id")
    Optional<ClassSession> findByIdForUpdate(@Param("id") Long id);
}
