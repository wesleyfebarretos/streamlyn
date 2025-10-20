package com.streamlyn.api.web.http.tus;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.exception.MultiPartUploadException;
import com.streamlyn.api.domain.inputs.CreateVideoUploadInput;
import com.streamlyn.api.domain.inputs.UploadVideoInput;
import com.streamlyn.api.domain.inputs.UploadVideoPartInput;
import com.streamlyn.api.domain.interfaces.ObjectStorageMultiPartUploaderService;
import com.streamlyn.api.domain.services.VideoService;
import com.streamlyn.entities.Video;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
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
    private final ObjectStorageMultiPartUploaderService multiPartUploaderService;

    public void protocolConfiguration(HttpServletResponse res) {
        TusUploadHeaderWriteService headerWriteService = new TusUploadHeaderWriteService(res, env);
        headerWriteService.writeProtocolVersion();
        headerWriteService.writeSupportedVersions();
        headerWriteService.writeUploadMaxSize();
        headerWriteService.writeExtensions();
        headerWriteService.writeMinChunkSize(multiPartUploaderService.minPartSize());
    }

    public void getUploadOffset(HttpServletResponse res, String videoId) {
        TusUploadHeaderWriteService headerWriteService = new TusUploadHeaderWriteService(res, env);
        headerWriteService.writeProtocolVersion();

        Video video = findVideoByIdOrThrow(videoId);

        headerWriteService.writeUploadOffset(video.getOffset());
        headerWriteService.writeNoStoreCacheControl();

        if(video.getUploadLength() != null) {
            headerWriteService.writeUploadLength(video.getUploadLength());
            headerWriteService.writeMinChunkSize(multiPartUploaderService.minPartSizeOf(video.getUploadLength()));
        }

        log.info("video offset retrieved for id: {}, offset = {}", video.getId(), video.getOffset());
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

        Video video = videoService.save(videoInput);

        if(headerReadService.getUploadLength().isPresent() && headerReadService.getUploadLength().get() > multiPartUploaderService.minPartSize()) {
            videoService.startMultiPartUpload(video);

            headerWriteService.writeMinChunkSize(multiPartUploaderService.minPartSizeOf(headerReadService.getUploadLength().get()));
        }

        headerWriteService.writeLocation(env.getProperty("app.url") + "/tus/videos/" + video.getId());
    }

    public void uploadPart(HttpServletRequest req, HttpServletResponse res, String videoId) {
        // TODO: - Handle Header Upload-Defer-Length
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

        if(headerReadService.getUploadLength().isPresent()) {
            headerWriteService.writeMinChunkSize(multiPartUploaderService.minPartSizeOf(headerReadService.getUploadLength().get()));
        }

        try (InputStream inputStream = req.getInputStream()) {
            Video video = findVideoByIdOrThrow(videoId);

            if(video.getUploadLength() > multiPartUploaderService.minPartSize()) {
                videoService.uploadPart(new UploadVideoPartInput(
                        videoId,
                        offset.get(),
                        inputStream,
                        contentLength.get()
                ));
            } else {
                videoService.upload(new UploadVideoInput(
                        videoId,
                        inputStream,
                        contentLength.get()
                ));
            }
        } catch (IOException e) {
            throw ApiException.internalServerError("failed to process upload");
        }

        long newOffset = offset.get() + contentLength.get();

        headerWriteService.writeProtocolVersion();
        headerWriteService.writeUploadOffset(newOffset);
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

    private Video findVideoByIdOrThrow(String id) {
        return videoService.findById(id).orElseThrow(() -> {
            log.error("video with id {} not found", id);
            return ApiException.notFound("video not found");
        });
    }
}
