package com.streamlyn.api.domain.services;

import com.streamlyn.api.domain.repositories.UserRepository;
import com.streamlyn.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User create(User newUser) {
        return userRepository.save(newUser);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }
}
