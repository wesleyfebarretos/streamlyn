package com.streamlyn.api.domain.services;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.exception.MultiPartUploadException;
import com.streamlyn.api.domain.inputs.CreateVideoUploadInput;
import com.streamlyn.api.domain.inputs.UploadVideoInput;
import com.streamlyn.api.domain.inputs.UploadVideoPartInput;
import com.streamlyn.api.domain.interfaces.ObjectStorageMultiPartUploaderService;
import com.streamlyn.api.domain.interfaces.ObjectStorageUploaderService;
import com.streamlyn.api.domain.repositories.VideoRepository;
import com.streamlyn.entities.Video;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class VideoService {
    private final VideoRepository videoRepository;

    @Value("${tus.max-size}")
    private Long FILE_MAX_SIZE;

    private final ObjectStorageMultiPartUploaderService multiPartUploaderService;
    private final ObjectStorageUploaderService uploaderService;

    public List<Video> findALl() {
        return videoRepository.findAll();
    }


    public Optional<Video> findById(String id) {
        return videoRepository.findById(id);
    }

    public Video save(@Valid CreateVideoUploadInput videoInput) {
        if (videoInput.uploadLength() != null && videoInput.uploadLength() > FILE_MAX_SIZE) {
            throw ApiException.payloadTooLarge("max file size exceeded");
        }

        Video video = Video.builder()
                .title(videoInput.title())
                .mimeType(videoInput.filetype())
                .fileName(videoInput.filename())
                .tags(videoInput.tags())
                .description(videoInput.description())
                .uploadLength(videoInput.uploadLength())
                .metadata(videoInput.metadata())
                .offset(0L)
                .build();

        videoRepository.save(video);

        return video;
    }

    public void startMultiPartUpload(Video video) {
        String extension = getVideoExtension(video);

        multiPartUploaderService.start(String.format("%s%s", video.getId(), extension), video.getMimeType());

        log.info("Multi part upload started, new empty video {}", video);
    }

    public void uploadPart(@Valid UploadVideoPartInput input) {
        Video video = findByIdOrThrow(input.videoId());

        if (input.offset() != video.getOffset()) {
            throw ApiException.conflict("provided offset does not match with the current upload offset");
        }

        validateUploadProgress(video);

        if (isNotLastPartAndLessThanMinPartSize(video, input)) {
            throw ApiException.badRequest(
                    String.format("Chunk size to small. The minimum Allowed for this upload is %d bytes, except the last one.", multiPartUploaderService.minPartSizeOf(video.getUploadLength()))
            );
        }

        String extension = getVideoExtension(video);

        long writtenBytes = 0L;

        String filePath = String.format("%s%s", video.getId(), extension);

        try {
            writtenBytes = multiPartUploaderService.uploadPart(filePath, input.data(), input.contentLength());
        } catch (MultiPartUploadException e) {
            writtenBytes = e.getWrittenBytes();
            throw ApiException.internalServerError(e.getMessage());
        } finally {
            video.setOffset(video.getOffset() + writtenBytes);

            if (video.getOffset().equals(video.getUploadLength())) {
                video.setFileUrl(multiPartUploaderService.complete(filePath));
                log.info("Multi part upload completed for video {}. File URL -> {}", video.getId(), video.getFileUrl());
            }

            videoRepository.save(video);
        }

        double progress = ((double) video.getOffset() / video.getUploadLength()) * 100;

        log.info("Uploaded new chunk for upload id {}: Progress={}%, Content Length={}, Current Offset={}, Upload Length={}",
                video.getId(), String.format("%.2f", progress), input.contentLength(), video.getOffset(), video.getUploadLength());
    }

    public void upload(@Valid UploadVideoInput input) {
        Video video = findByIdOrThrow(input.videoId());

        validateUploadProgress(video);

        if (video.getOffset() + input.contentLength() < video.getUploadLength()) {
            throw ApiException.badRequest(
                String.format(
                    "Upload payload too small: total video size is %d bytes, but the request only provided %d bytes.",
                    video.getUploadLength(), input.contentLength()
                )
            );
        }

        String extension = getVideoExtension(video);

        String filePath = String.format("%s%s", video.getId(), extension);

        video.setFileUrl(uploaderService.upload(filePath, input.data(), video.getMimeType()));
        video.setOffset(video.getUploadLength());

        videoRepository.save(video);

        log.info("uploaded new video for upload id {}: Upload Length={}", video.getId(), video.getUploadLength());
    }

    private Video findByIdOrThrow(String id) {
        return videoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Video not found"));
    }

    private void validateUploadProgress(Video video) {
        if (video.getOffset().equals(video.getUploadLength())) {
            throw ApiException.conflict("The file has already been uploaded");
        }
    }

    private String getVideoExtension(Video video) {
        try {
            return MimeTypes.getDefaultMimeTypes()
                    .forName(video.getMimeType())
                    .getExtension();
        } catch (MimeTypeException e) {
            throw ApiException.unsupportedMediaType(String.format("invalid video mimetype: %s.", video.getMimeType()));
        }
    }

    private boolean isNotLastPartAndLessThanMinPartSize(Video video, UploadVideoPartInput input) {
        return video.getOffset() + input.contentLength() < video.getUploadLength() &&
                input.contentLength() < multiPartUploaderService.minPartSizeOf(video.getUploadLength());
    }
}
