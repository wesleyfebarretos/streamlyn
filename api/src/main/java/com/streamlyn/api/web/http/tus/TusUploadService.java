package com.streamlyn.api.web.http.tus;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.inputs.CreateVideoUploadInput;
import com.streamlyn.api.domain.inputs.UploadVideoChunkInput;
import com.streamlyn.api.domain.services.VideoService;
import com.streamlyn.entities.Video;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TusUploadService {
    private final Environment env;
    private final VideoService videoService;

    public void protocolConfiguration(HttpServletResponse res) {
        TusUploadHeaderWriteService headerWriteService = new TusUploadHeaderWriteService(res, env);
        headerWriteService.writeProtocolVersion();
        headerWriteService.writeSupportedVersions();
        headerWriteService.writeUploadMaxSize();
        headerWriteService.writeExtensions();
    }

    public void getUploadOffset(HttpServletResponse res, String fileId) {
        TusUploadHeaderWriteService headerWriteService = new TusUploadHeaderWriteService(res, env);
        headerWriteService.writeProtocolVersion();

        Video video = videoService.findById(fileId).orElseThrow(() -> {
            log.error("file with id '{}' not found", fileId);
            return ApiException.notFound("video not found");
        });

        headerWriteService.writeUploadOffset(video.getOffset());
        headerWriteService.writeNoStoreCacheControl();

        if(video.getUploadLength() != null) {
            headerWriteService.writeUploadLength(video.getUploadLength());
        }

        log.info("file offset retrieved for id: {}, offset = {}", video.getId(), video.getOffset());
    }

    public void createUpload(HttpServletRequest req, HttpServletResponse res) {
        // TODO:
        //  - Handle Header Upload-Defer-Length
        //  - Handle with the expiration
        TusUploadHeaderReadService headerReadService = new TusUploadHeaderReadService(req);
        TusUploadHeaderWriteService headerWriteService = new TusUploadHeaderWriteService(res, env);

        headerWriteService.writeProtocolVersion();

        TusUploadMetadataReadService metadataReadService = new TusUploadMetadataReadService(req);
        validateRequiredMetadata(metadataReadService);

        CreateVideoUploadInput videoInput = new CreateVideoUploadInput(
                metadataReadService.getTitle().get(),
                metadataReadService.getFileName().get(),
                metadataReadService.getFileType().get(),
                metadataReadService.getTags(),
                metadataReadService.getDescription().orElse(null),
                headerReadService.getUploadLength().orElse(null),
                headerReadService.getMetadata().get()
        );

        Video video = videoService.createUpload(videoInput);

        headerWriteService.writeLocation("/files/" + video.getId());
    }

    private void validateRequiredMetadata(TusUploadMetadataReadService metadataService) {
        List<String> requiredFields = new ArrayList<>();

        if (metadataService.getTitle().isEmpty()) {
            requiredFields.add("title");
        }

        if (metadataService.getFileName().isEmpty()) {
            requiredFields.add("filename");
        }

        if (metadataService.getFileType().isEmpty()) {
            requiredFields.add("filetype");
        }

        if (!requiredFields.isEmpty()) {
            throw ApiException.badRequest(String.format("missing %s in Upload-Metadata", Strings.join(requiredFields, ',')));
        }
    }

    public void uploadChunk(HttpServletRequest req, HttpServletResponse res, String fileId) {
        TusUploadHeaderReadService headerReadService = new TusUploadHeaderReadService(req);
        TusUploadHeaderWriteService headerWriteService = new TusUploadHeaderWriteService(res, env);

        Optional<String> contentType = headerReadService.getContentType();
        if (contentType.isEmpty() || !contentType.get().equals("application/offset+octet-stream")) {
            throw ApiException.unsupportedMediaType("Invalid Content-Type header");
        }

        Optional<Long> offset = headerReadService.getUploadOffset();
        Optional<Long> contentLength = headerReadService.getContentLength();

        if (offset.isEmpty()) {
            throw ApiException.conflict("missing Upload-Offset header");
        }

        if (contentLength.isEmpty()) {
            throw ApiException.badRequest("missing Content-length header");
        }

        try (InputStream inputStream = req.getInputStream()) {
            videoService.uploadChunk(new UploadVideoChunkInput(
                    fileId,
                    offset.get(),
                    inputStream,
                    contentLength.get()
            ));
        } catch (IOException e) {
            throw ApiException.internalServerError("failed to process upload");
        }

        long newOffset = offset.get() + contentLength.get();

        headerWriteService.writeProtocolVersion();
        headerWriteService.writeUploadOffset(newOffset);
    }
}
