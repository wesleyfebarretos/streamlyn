package com.streamlyn.api.domain.inputs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.InputStream;

public record UploadVideoChunkInput(
        @NotBlank(message = "fileId cannot be empty")
        @Size(max = 50, message = "fileId is not a valid id")
        String fileId,

        @Min(value = 0, message = "offset must be a non-negative integer")
        long offset,

        @NotNull(message = "data stream cannot be null")
        InputStream data,

        @Min(value = 0, message = "contentLength must be a non-negative integer")
        long contentLength
) {}
