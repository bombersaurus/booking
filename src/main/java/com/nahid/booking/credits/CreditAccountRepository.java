package com.nahid.booking.credits;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {
    // IDs only: loading the entity here would put an unlocked copy in the
    // persistence context, and a later locking query would return that stale copy.
    @Query("select a.id from CreditAccount a where a.userId = :userId")
    Optional<Long> findIdByUserId(@Param("userId") Long userId);

    // Only for RESERVED and ISSUED, which the schema limits to one account each.
    @Query("select a.id from CreditAccount a where a.kind = :kind")
    Optional<Long> findIdByKind(@Param("kind") AccountKind kind);

    // SELECT ... FOR UPDATE: waits for any transaction holding this row, then blocks others until commit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccount a where a.id = :id")
    Optional<CreditAccount> findByIdForUpdate(@Param("id") Long id);
}
