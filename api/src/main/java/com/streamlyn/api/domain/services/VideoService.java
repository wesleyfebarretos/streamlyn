package com.streamlyn.api.domain.services;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.inputs.CreateVideoUploadInput;
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

    public void updateOffset(String id, Long offset) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("video not found"));

        video.setOffset(offset);

        videoRepository.save(video);
    }

    public Video createUpload(@Valid CreateVideoUploadInput videoInput) {
        if (videoInput.uploadLength() != null && videoInput.uploadLength() > FILE_MAX_SIZE) {
            throw ApiException.payloadTooLarge("max file size exceeded");
        }

        try {
            String extension = MimeTypes.getDefaultMimeTypes()
                    .forName(videoInput.filetype()).getExtension();

            if(extension.isBlank()) {
                throw ApiException.badRequest("invalid filetype in Upload-Metadata" + videoInput.filetype());
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
                .offset(0L)
                .build();

        save(video);

        video.setFileUrl(storageService.createUpload(video.getId()));

        videoRepository.save(video);

        log.info("new empty video created: {}", video);

        return video;
    }
}
