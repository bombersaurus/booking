package com.nahid.booking.catalog;

import com.nahid.booking.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ClassSessionService {
    private final ClassSessionRepository sessions;

    public ClassSessionService(ClassSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<ClassSessionResponse> list() {
        return sessions.findAllByOrderByStartsAtAscIdAsc().stream()
                .map(ClassSessionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClassSessionResponse get(Long id) {
        return sessions.findById(id).map(ClassSessionResponse::from)
                .orElseThrow(() -> ApiException.notFound("Class session not found."));
    }

    @Transactional
    public ClassSessionResponse create(CreateClassSessionRequest request) {
        // Truncate to PostgreSQL's precision so the response matches the stored value.
        ClassSession session = new ClassSession(request.name().trim(),
                request.startsAt().truncatedTo(ChronoUnit.MICROS), request.capacity(), request.creditCost());
        return ClassSessionResponse.from(sessions.save(session));
    }
}

