package com.nahid.booking.booking;

import com.nahid.booking.catalog.ClassSession;
import com.nahid.booking.catalog.ClassSessionRepository;
import com.nahid.booking.credits.CreditService;
import com.nahid.booking.shared.ApiException;
import com.nahid.booking.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final ClassSessionRepository sessions;
    private final UserRepository users;
    private final CreditService credits;
    private final Clock clock;

    public BookingService(BookingRepository bookings, ClassSessionRepository sessions,
                          UserRepository users, CreditService credits, Clock clock) {
        this.bookings = bookings;
        this.sessions = sessions;
        this.users = users;
        this.credits = credits;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        requireUser(request.userId());
        // Lock order for every write: the class or booking row first, then credit
        // accounts in ascending id order (see CreditService). Locking the class makes
        // concurrent bookings for it queue here, one transaction at a time.
        ClassSession session = sessions.findByIdForUpdate(request.classSessionId())
                .orElseThrow(() -> ApiException.notFound("Class session not found."));
        if (session.getCancelledAt() != null || !session.getStartsAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.CONFLICT, "This class is no longer open for booking.");
        }
        if (bookings.existsByUserIdAndClassSessionIdAndStatusNot(
                request.userId(), session.getId(), BookingStatus.CANCELLED)) {
            throw new ApiException(HttpStatus.CONFLICT, "This user already has an active booking for this class.");
        }
        // The count is exact: the next booking for this class is waiting on the class
        // lock and will only count once this transaction has committed or rolled back.
        if (bookings.countByClassSessionIdAndStatus(session.getId(), BookingStatus.CONFIRMED)
                >= session.getCapacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "This class is full.");
        }
        Booking booking = bookings.saveAndFlush(new Booking(request.userId(), session.getId(), clock.instant()));
        // The reservation joins this transaction. If it fails, for example because
        // the member lacks credits, the booking insert above is rolled back too.
        credits.reserve(request.userId(), booking.getId(), session.getCreditCost());
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id) {
        return bookings.findById(id).map(BookingResponse::from)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
    }

    @Transactional
    public void cancel(Long id) {
        // Concurrent cancellations of one booking queue on this lock. The later ones
        // then read CANCELLED and return without a second refund.
        Booking booking = bookings.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }
        booking.cancel(clock.instant());
        credits.refund(booking.getId());
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForUser(Long userId) {
        requireUser(userId);
        return bookings.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(BookingResponse::from).toList();
    }

    private void requireUser(Long id) {
        if (!users.existsById(id)) {
            throw ApiException.notFound("User not found.");
        }
    }
}
