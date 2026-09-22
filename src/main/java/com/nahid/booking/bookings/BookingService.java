package com.nahid.booking.bookings;

import com.nahid.booking.classes.ClassSession;
import com.nahid.booking.classes.ClassSessionRepository;
import com.nahid.booking.users.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final ClassSessionRepository classes;
    private final UserRepository users;

    public BookingService(BookingRepository bookings, ClassSessionRepository classes, UserRepository users) {
        this.bookings = bookings;
        this.classes = classes;
        this.users = users;
    }

    @Transactional
    public BookingResponse book(BookingRequest request) {
        if (!users.existsById(request.userId())) {
            throw new ResponseStatusException(NOT_FOUND, "User not found.");
        }

        ClassSession session = classes.findById(request.classSessionId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Class not found."));

        if (session.hasStarted()) {
            throw new ResponseStatusException(CONFLICT, "This class has already started.");
        }

        boolean alreadyBooked = bookings.existsByUserIdAndClassSessionIdAndStatus(
                request.userId(), session.getId(), BookingStatus.CONFIRMED);
        if (alreadyBooked) {
            throw new ResponseStatusException(CONFLICT, "You have already booked this class.");
        }

        long placesTaken = bookings.countByClassSessionIdAndStatus(session.getId(), BookingStatus.CONFIRMED);
        if (placesTaken >= session.getCapacity()) {
            throw new ResponseStatusException(CONFLICT, "This class is full.");
        }

        Booking booking = bookings.save(new Booking(request.userId(), session.getId()));
        return BookingResponse.from(booking);
    }

    @Transactional
    public void cancel(Long bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Booking not found."));
        booking.cancel();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> bookingsForUser(Long userId) {
        if (!users.existsById(userId)) {
            throw new ResponseStatusException(NOT_FOUND, "User not found.");
        }

        return bookings.findByUserIdOrderByIdDesc(userId)
                .stream()
                .map(BookingResponse::from)
                .toList();
    }
}
