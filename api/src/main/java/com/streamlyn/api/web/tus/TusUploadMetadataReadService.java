package com.streamlyn.api.web.tus;

import com.streamlyn.api.domain.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.*;
import java.util.stream.Stream;

public class TusUploadMetadataReadService {
    private final List<String> metadata;

    public TusUploadMetadataReadService(HttpServletRequest req) {
        this.metadata = Stream.of(req.getHeader("Upload-Metadata").split(","))
                .map(String::trim)
                .toList();
    }

    public Optional<String> getFileName() {
        return getMetadata("filename");
    }

    public Optional<String> getTitle() {
        return getMetadata("title");
    }

    public Optional<String> getDescription() {
        return getMetadata("description");
    }

    public List<String> getTags() {
        return getMetadata("tags")
                .stream()
                .flatMap(tags -> Arrays.stream(tags.split(",")))
                .toList();
    }

    public Optional<String> getFileType() {
        return getMetadata("filetype");
    }

    public boolean isEmpty() {
        return metadata.isEmpty();
    }

    private Optional<String> getMetadata(String key) {
        String k = key.concat(" ");
        return metadata
                .stream()
                .filter(chunk -> chunk.startsWith(k))
                .map(chunk -> chunk.substring(k.length()))
                .filter(value -> !value.isEmpty())
                .map(this::decode)
                .map(String::trim)
                .findFirst();
    }

    private String decode(String valueB64) {
        try {
            return new String(Base64.getDecoder().decode(valueB64));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid base64-encoded value in Upload-Metadata");
        }
    }
}
