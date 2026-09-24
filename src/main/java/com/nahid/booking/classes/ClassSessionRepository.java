package com.nahid.booking.classes;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findAllByOrderByStartsAtAsc();

    // Runs SELECT ... FOR UPDATE. Other transactions that want this row
    // have to wait until this one commits or rolls back.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClassSession c where c.id = :id")
    Optional<ClassSession> findByIdForUpdate(@Param("id") Long id);
}
