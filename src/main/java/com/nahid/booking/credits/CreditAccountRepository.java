package com.nahid.booking.credits;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {
    Optional<CreditAccount> findByUserId(Long userId);
    // Only for RESERVED and ISSUED, which the schema limits to one account each.
    Optional<CreditAccount> findByKind(AccountKind kind);
}
