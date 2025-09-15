package com.streamlyn.api.web.tus;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;

@RequiredArgsConstructor
public class TusUploadHeaderWriteService {
    private final HttpServletResponse res;
    private final Environment env;

    public void writeProtocolVersion() {
        res.setHeader("Tus-Resumable", "1.0.0");
    }

    public void writeProtocolVersion(String version) {
        res.setHeader("Tus-Resumable", version);
    }

    public void writeSupportedVersions() {
        res.setHeader("Tus-Version", "1.0.0,0.2.2,0.2.1");
    }

    public void writeSupportedVersions(String versions) {
        res.setHeader("Tus-Version", versions);
    }

    public void writeUploadMaxSize() {
        res.setHeader("Tus-Max-Size", env.getProperty("tus.max-size"));
    }

    public void writeUploadMaxSize(String maxSize) {
        res.setHeader("Tus-Max-Size", maxSize);
    }

    public void writeExtensions() {
        res.setHeader("Tus-Extension", "creation,expiration");
    }

    public void writeExtensions(String extensions) {
        res.setHeader("Tus-Extension", extensions);
    }

    public void writeLocation(String fileLocation) {
        res.setHeader("Location", fileLocation);
    }

    public void writeUploadOffset(long offset) {
        res.setHeader("Upload-Offset", String.valueOf(offset));
    }

    public void writeUploadLength(long length) {
        res.setHeader("Upload-Length", String.valueOf(length));
    }

    public void writeNoStoreCacheControl() {
        res.setHeader("Cache-Control", "no-store");
    }
}
