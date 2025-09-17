package com.streamlyn.api.web.http.controllers.User;

import com.streamlyn.api.domain.services.UserService;
import com.streamlyn.entities.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("users")
public class UserController {
    private final UserService userService;

    @PostMapping
    public User create(@RequestBody @Valid CreateUserRequest newUser) {
        return userService.create(
            User.builder()
                    .name(newUser.name())
                    .email(newUser.email())
                    .password(newUser.password())
                    .build()
        );
    }

    @GetMapping
    public List<User> findAll() {
        return userService.findAll();
    }
}
