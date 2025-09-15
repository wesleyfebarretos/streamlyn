package com.streamlyn.api.domain.services;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.inputs.CreateVideoUploadInput;
import com.streamlyn.api.domain.inputs.UploadVideoChunkInput;
import com.streamlyn.api.domain.interfaces.UploadStorageService;
import com.streamlyn.api.domain.repositories.VideoRepository;
import com.streamlyn.entities.Video;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final UploadStorageService storageService;

    public List<Video> findALl() {
        return videoRepository.findAll();
    }

    public void save(Video video) {
        videoRepository.save(video);
    }

    public Optional<Video> findById(String id) {
        return videoRepository.findById(id);
    }

    @Transactional
    public Video createUpload(@Valid CreateVideoUploadInput videoInput) {
        if (videoInput.uploadLength() != null && videoInput.uploadLength() > FILE_MAX_SIZE) {
            throw ApiException.payloadTooLarge("max file size exceeded");
        }

        String extension = "";

        try {
            extension = MimeTypes.getDefaultMimeTypes()
                    .forName(videoInput.filetype()).getExtension();

            if (extension.isBlank()) {
                throw ApiException.badRequest("invalid filetype: " + videoInput.filetype());
            }
        } catch (MimeTypeException e) {
            throw ApiException.badRequest(e.getMessage());
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

        save(video);

        video.setFileUrl(storageService.createUpload(String.format("%s%s", video.getId(), extension)));

        videoRepository.save(video);

        log.info("new empty video created: {}", video);

        return video;
    }

    @Transactional
    public void uploadChunk(@Valid UploadVideoChunkInput input) {
        Video video = videoRepository.findById(input.fileId())
                .orElseThrow(() -> ApiException.notFound("video not found"));

        if (input.offset() != video.getOffset()) {
            throw ApiException.conflict("provided offset does not match with the current upload offset");
        }

        if (video.getOffset().equals(video.getUploadLength())) {
            throw ApiException.conflict("The file has already been uploaded");
        }

        long writtenBytes = storageService.writeChunk(video.getFileUrl(), input.offset(), input.data(), input.contentLength());

        long newOffset = video.getOffset() + writtenBytes;
        video.setOffset(newOffset);

        videoRepository.save(video);

        if (newOffset == video.getUploadLength()) {
            storageService.finalizeUpload(video.getFileUrl());
        }

        log.info("uploaded new chunk for upload id {}: Content-Length={}, previous offset={}, current offset={}, upload length={}",
                video.getId(), input.contentLength(), input.offset(), newOffset, video.getUploadLength());

        if(writtenBytes != input.contentLength()) {
            throw ApiException.internalServerError("could not write all bytes of the request");
        }
    }
}
