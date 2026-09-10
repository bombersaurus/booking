package com.nahid.booking.users;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll(Sort.by("id")).stream()
                .map(user -> new UserResponse(user.getId(), user.getEmail())).toList();
    }
}

