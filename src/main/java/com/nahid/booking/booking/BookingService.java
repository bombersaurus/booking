package com.nahid.booking.booking;

import com.nahid.booking.catalog.ClassSession;
import com.nahid.booking.catalog.ClassSessionRepository;
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
    private final Clock clock;

    public BookingService(BookingRepository bookings, ClassSessionRepository sessions,
                          UserRepository users, Clock clock) {
        this.bookings = bookings;
        this.sessions = sessions;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        requireUser(request.userId());
        ClassSession session = sessions.findById(request.classSessionId())
                .orElseThrow(() -> ApiException.notFound("Class session not found."));
        if (session.getCancelledAt() != null || !session.getStartsAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.CONFLICT, "This class is no longer open for booking.");
        }
        if (bookings.existsByUserIdAndClassSessionIdAndStatusNot(
                request.userId(), session.getId(), BookingStatus.CANCELLED)) {
            throw new ApiException(HttpStatus.CONFLICT, "This user already has an active booking for this class.");
        }
        // V1 deliberately uses a count followed by an insert. V3 will demonstrate
        // the race between these statements and introduce fixed-order row locks.
        if (bookings.countByClassSessionIdAndStatus(session.getId(), BookingStatus.CONFIRMED)
                >= session.getCapacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "This class is full.");
        }
        return BookingResponse.from(bookings.saveAndFlush(
                new Booking(request.userId(), session.getId(), clock.instant())));
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id) {
        return bookings.findById(id).map(BookingResponse::from)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
    }

    @Transactional
    public void cancel(Long id) {
        Booking booking = bookings.findById(id)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }
        booking.cancel(clock.instant());
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

