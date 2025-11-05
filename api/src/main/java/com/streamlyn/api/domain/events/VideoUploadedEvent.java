package com.streamlyn.api.domain.events;

public record VideoUploadedEvent(
        String fileURL,
        String id
) {}
