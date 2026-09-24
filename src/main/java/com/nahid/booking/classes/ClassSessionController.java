package com.nahid.booking.classes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class ClassSessionController {

    private final ClassSessionRepository classes;

    public ClassSessionController(ClassSessionRepository classes) {
        this.classes = classes;
    }

    @GetMapping("/api/v1/classes")
    public List<ClassSessionResponse> listClasses() {
        return classes.findAllByOrderByStartsAtAsc()
                .stream()
                .map(ClassSessionResponse::from)
                .toList();
    }
}
