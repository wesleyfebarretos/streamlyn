package com.streamlyn.api.web.tus;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.inputs.CreateVideoUploadInput;
import com.streamlyn.api.domain.interfaces.UploadStorageService;
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
    private final UploadStorageService storageService;
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
        Video video = findFileById(fileId);
        log.info("file offset retrieved for id: {}, offset = {}", video.getId(), video.getOffset());
        headerWriteService.writeUploadOffset(video.getOffset());
        headerWriteService.writeNoStoreCacheControl();
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
                headerReadService.getUploadLength().orElse(null)
        );

        Video video = videoService.createUpload(videoInput);

        headerWriteService.writeLocation("/files/" + video.getId());
    }

    private void validateRequiredMetadata(TusUploadMetadataReadService metadataService) {
        List<String> requiredFields = new ArrayList<>();

        if(metadataService.getTitle().isEmpty()) {
            requiredFields.add("title");
        }

        if(metadataService.getFileName().isEmpty()) {
            requiredFields.add("filename");
        }

        if(metadataService.getFileType().isEmpty()) {
            requiredFields.add("filetype");
        }

        if(!requiredFields.isEmpty()) {
            throw ApiException.badRequest(String.format("missing %s in Upload-Metadata", Strings.join(requiredFields, ',')));
        }
    }

    public void uploadChunk(HttpServletRequest req, HttpServletResponse res, String fileId) {
        // TODO: Move the business logic to a service
        TusUploadHeaderReadService headerReadService = new TusUploadHeaderReadService(req);
        TusUploadHeaderWriteService headerWriteService = new TusUploadHeaderWriteService(res, env);

        Optional<String> contentType = headerReadService.getContentType();
        if (contentType.isEmpty() || !contentType.get().equals("application/offset+octet-stream")) {
            throw ApiException.unsupportedMediaType("Invalid Content-Type header");
        }

        Video video = findFileById(fileId);

        Optional<Long> offset = headerReadService.getUploadOffset();
        Optional<Long> contentLength = headerReadService.getContentLength();

        if(offset.isEmpty() || !offset.get().equals(video.getOffset())) {
            throw ApiException.conflict("provided offset does not match with the current upload offset");
        }

        if(contentLength.isEmpty()) {
            throw ApiException.badRequest("missing Content-length header");
        }

        try (InputStream inputStream = req.getInputStream()){
            storageService.writeChunk(video.getId(), offset.get(), inputStream, contentLength.get());
        } catch (IOException e) {
            throw ApiException.internalServerError("failed to process upload");
        }

        long newOffset = video.getOffset() + contentLength.get();

        videoService.updateOffset(video.getId(), newOffset);

        log.info("uploaded new chunk for upload id {}: Content-Length={}, previous offset={}, current offset={}",
                video.getId(), contentLength.get(), offset.get(), newOffset);

        headerWriteService.writeProtocolVersion();
        headerWriteService.writeUploadOffset(newOffset);
    }

    private Video findFileById(String fileId) {
        return videoService.findById(fileId).orElseThrow(() -> {
            log.error("file with id '{}' not found", fileId);
            return ApiException.notFound("video not found");
        });
    }
}
