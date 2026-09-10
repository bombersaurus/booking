package com.nahid.booking.credits;

import com.nahid.booking.shared.ApiException;
import com.nahid.booking.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class CreditService {
    private final CreditAccountRepository accounts;
    private final CreditTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final UserRepository users;
    private final Clock clock;

    public CreditService(CreditAccountRepository accounts, CreditTransactionRepository transactions,
                         LedgerEntryRepository entries, UserRepository users, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CreditStatementResponse statement(Long userId) {
        CreditAccount account = memberAccount(userId);
        return new CreditStatementResponse(userId, account.getBalance(), entries.statementFor(account.getId()));
    }

    @Transactional
    public CreditStatementResponse topUp(Long userId, long amount) {
        post(TransactionKind.TOP_UP, null, systemAccount(AccountKind.ISSUED), memberAccount(userId), amount);
        return statement(userId);
    }

    // Called from BookingService, this joins the booking's transaction, so the
    // booking and its credits are committed or rolled back together.
    @Transactional
    public void reserve(Long userId, Long bookingId, long amount) {
        CreditAccount member = memberAccount(userId);
        if (member.getBalance() < amount) {
            throw new ApiException(HttpStatus.CONFLICT, "Not enough credits for this class.");
        }
        post(TransactionKind.RESERVATION, bookingId, member, systemAccount(AccountKind.RESERVED), amount);
    }

    // Returns exactly what was reserved. Bookings made before credits existed
    // have no reservation, so there is nothing to refund.
    @Transactional
    public void refund(Long bookingId) {
        if (transactions.existsByBookingIdAndKind(bookingId, TransactionKind.REFUND)) {
            return;
        }
        transactions.findByBookingIdAndKind(bookingId, TransactionKind.RESERVATION).ifPresent(reservation -> {
            LedgerEntry memberDebit = entries.findByTransactionId(reservation.getId()).stream()
                    .filter(entry -> entry.getAmount() < 0).findFirst().orElseThrow();
            CreditAccount member = accounts.findById(memberDebit.getAccountId()).orElseThrow();
            post(TransactionKind.REFUND, bookingId, systemAccount(AccountKind.RESERVED), member, -memberDebit.getAmount());
        });
    }

    // One balanced transaction: the same amount leaves one account and enters another.
    private void post(TransactionKind kind, Long bookingId, CreditAccount from, CreditAccount to, long amount) {
        Instant now = clock.instant();
        CreditTransaction transaction = transactions.save(new CreditTransaction(kind, bookingId, now));
        entries.saveAll(List.of(
                new LedgerEntry(transaction.getId(), from.getId(), -amount, now),
                new LedgerEntry(transaction.getId(), to.getId(), amount, now)));
        from.apply(-amount);
        to.apply(amount);
    }

    private CreditAccount memberAccount(Long userId) {
        if (!users.existsById(userId)) {
            throw ApiException.notFound("User not found.");
        }
        return accounts.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " has no credit account."));
    }

    private CreditAccount systemAccount(AccountKind kind) {
        return accounts.findByKind(kind)
                .orElseThrow(() -> new IllegalStateException("Missing " + kind + " credit account."));
    }
}
