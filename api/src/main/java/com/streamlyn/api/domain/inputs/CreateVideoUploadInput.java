package com.streamlyn.api.domain.inputs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateVideoUploadInput(
        @NotBlank(message = "title cannot be empty")
        @Size(max = 100, message = "title cannot exceed 100 characters")
        String title,

        @NotBlank(message = "filename cannot be empty")
        @Size(max = 100, message = "filename cannot exceed 100 characters")
        String filename,

        @NotBlank(message = "filetype cannot be empty")
        String filetype,

        List<@NotBlank(message = "tags cannot contain blank strings")
        String> tags,

        @Size(max = 2000, message = "description cannot exceed 2000 characters")
        String description,

        @Min(value = 0, message = "uploadLength cannot be negative")
        Long uploadLength,

        @NotBlank(message = "metadata cannot be empty")
        @Size(max = 3000, message = "metadata cannot exceed 3000 characters")
        String metadata
) {}
