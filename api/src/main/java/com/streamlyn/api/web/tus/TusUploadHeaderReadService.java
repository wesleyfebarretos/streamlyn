package com.streamlyn.api.web.tus;

import com.streamlyn.api.domain.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class TusUploadHeaderReadService {
    private final HttpServletRequest req;

    public Optional<Long> getUploadLength() {
        Optional<Long> uploadLength = headerToLong("Upload-Length");
        uploadLength.ifPresent(value -> {
            assertNonNegativeInteger("Upload-Length", value);
        });
        return uploadLength;
    }

    public Optional<Long> getUploadOffset() {
        Optional<Long> uploadOffset = headerToLong("Upload-Offset");
        uploadOffset.ifPresent(value -> {
            assertNonNegativeInteger("Upload-Offset", value);
        });
        return uploadOffset;
    }

    public Optional<Long> getContentLength() {
        Optional<Long> contentLength = headerToLong("Content-Length");
        contentLength.ifPresent(value -> {
            assertNonNegativeInteger("Content-Length", value);
        });
        return contentLength;
    }

    public Optional<String> getContentType() {
        return Optional.ofNullable(req.getHeader("Content-Type"));
    }

    private Optional<Long> headerToLong(String header) {
        return Optional.ofNullable(req.getHeader(header))
                .map(v -> {
                    try {
                        return Long.parseLong(v);
                    } catch (NumberFormatException e) {
                        throw ApiException.badRequest(String.format("%s header must be a valid integer", header));
                    }
                });
    }

    private void assertNonNegativeInteger(String header, Long value) {
        if (value < 0) {
            throw ApiException.badRequest(String.format("%s header must be a valid non-negative integer", header));
        }
    }
}
