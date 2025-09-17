package com.streamlyn.api.web.http.controllers.User;

import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotEmpty(message = "name cannot be empty")
        @Size(max = 100, min = 3, message = "name has to be between 3 and 100 characters")
        String name,

        @Email(message = "invalid email")
        @NotEmpty(message = "email cannot be empty")
        String email,

        @NotEmpty(message = "password cannot be empty")
        String password
) {}
