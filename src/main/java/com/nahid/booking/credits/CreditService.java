package com.nahid.booking.credits;

import com.nahid.booking.shared.ApiException;
import com.nahid.booking.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Long accountId = memberAccountId(userId);
        CreditAccount account = accounts.findById(accountId).orElseThrow();
        return new CreditStatementResponse(userId, account.getBalance(), entries.statementFor(accountId));
    }

    @Transactional
    public CreditStatementResponse topUp(Long userId, long amount) {
        Long issued = systemAccountId(AccountKind.ISSUED);
        Long member = memberAccountId(userId);
        Map<Long, CreditAccount> locked = lockInIdOrder(issued, member);
        post(TransactionKind.TOP_UP, null, locked.get(issued), locked.get(member), amount);
        return statement(userId);
    }

    // Called from BookingService, this joins the booking's transaction, so the
    // booking and its credits are committed or rolled back together.
    @Transactional
    public void reserve(Long userId, Long bookingId, long amount) {
        Long member = memberAccountId(userId);
        Long reserved = systemAccountId(AccountKind.RESERVED);
        Map<Long, CreditAccount> locked = lockInIdOrder(member, reserved);
        // The balance is read under the lock, so no other transaction can spend it meanwhile.
        if (locked.get(member).getBalance() < amount) {
            throw new ApiException(HttpStatus.CONFLICT, "Not enough credits for this class.");
        }
        post(TransactionKind.RESERVATION, bookingId, locked.get(member), locked.get(reserved), amount);
    }

    // Returns exactly what was reserved. Bookings made before credits existed
    // have no reservation, so there is nothing to refund. The caller holds the
    // booking's row lock, so two refunds of one booking cannot run at once.
    @Transactional
    public void refund(Long bookingId) {
        if (transactions.existsByBookingIdAndKind(bookingId, TransactionKind.REFUND)) {
            return;
        }
        transactions.findByBookingIdAndKind(bookingId, TransactionKind.RESERVATION).ifPresent(reservation -> {
            LedgerEntry memberDebit = entries.findByTransactionId(reservation.getId()).stream()
                    .filter(entry -> entry.getAmount() < 0).findFirst().orElseThrow();
            Long reserved = systemAccountId(AccountKind.RESERVED);
            Map<Long, CreditAccount> locked = lockInIdOrder(reserved, memberDebit.getAccountId());
            post(TransactionKind.REFUND, bookingId, locked.get(reserved), locked.get(memberDebit.getAccountId()),
                    -memberDebit.getAmount());
        });
    }

    // Every path locks accounts in ascending id order. Two transactions that need
    // the same accounts therefore queue in the same order and cannot deadlock.
    private Map<Long, CreditAccount> lockInIdOrder(Long... ids) {
        Map<Long, CreditAccount> locked = new HashMap<>();
        for (Long id : Arrays.stream(ids).sorted().distinct().toList()) {
            locked.put(id, accounts.findByIdForUpdate(id).orElseThrow());
        }
        return locked;
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

    private Long memberAccountId(Long userId) {
        if (!users.existsById(userId)) {
            throw ApiException.notFound("User not found.");
        }
        return accounts.findIdByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " has no credit account."));
    }

    private Long systemAccountId(AccountKind kind) {
        return accounts.findIdByKind(kind)
                .orElseThrow(() -> new IllegalStateException("Missing " + kind + " credit account."));
    }
}
