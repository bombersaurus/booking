package com.nahid.booking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
    List<ClassSession> findAllByOrderByStartsAtAscIdAsc();
}

