package com.streamlyn.api.integration;

import com.streamlyn.api.domain.inputs.CreateVideoUploadInput;
import com.streamlyn.api.domain.repositories.VideoRepository;
import com.streamlyn.api.domain.services.VideoService;
import com.streamlyn.entities.Video;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MvcResult;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TusUploadControllerTest extends BaseIntegrationTest {
    private final VideoRepository videoRepository;
    private final VideoService videoService;
    private final TestRestTemplate restTemplate;

    @Nested
    class VideoIngesting {
        private InputStream getSampleVideoStream() throws Exception {
            InputStream inputStream = getClass().getResourceAsStream("/sample-video.mp4");
            if (inputStream == null) {
                throw new IllegalStateException("sample video file not found in resources");
            }

            return new BufferedInputStream(inputStream);
        }

        private long sampleVideoBytesLen() {
            return new File(Objects.requireNonNull(getClass().getResource("/sample-video.mp4")).getFile()).length();
        }

        private MvcResult createUploadRequest() {
            long fileBytesLen = sampleVideoBytesLen();

            String filename = Base64.getEncoder().encodeToString("sample-video.mp4".getBytes());
            String fileType = Base64.getEncoder().encodeToString("video/mp4".getBytes());
            String title = Base64.getEncoder().encodeToString("SAMPLE VIDEO".getBytes());

            try {
                return mockMvc.perform(
                                post("/files")
                                        .header("Upload-Length", Long.toString(fileBytesLen))
                                        .header("Upload-Metadata", String.format(
                                                "filename %s,filetype %s,title %s", filename, fileType, title
                                        ))
                                        .header("Tus-Resumable", "1.0.0")
                                        .header("Content-Length", 0)
                        )
                        .andDo(print())
                        .andExpect(status().isCreated())
                        .andExpect(header().exists("Location"))
                        .andExpect(header().exists("Tus-Resumable"))
                        .andExpect(header().string("Tus-Resumable", "1.0.0"))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private int writeChunk(Video video, byte[] chunk, long offset) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/offset+octet-stream");
            headers.add("Content-Length", String.valueOf(chunk.length));
            headers.add("Tus-Resumable", "1.0.0");
            headers.add("Upload-Offset", String.valueOf(offset));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(chunk, headers);

            String path = "/files/" + video.getId();

            ResponseEntity<Void> response = restTemplate.exchange(path, HttpMethod.PATCH, requestEntity, Void.class);

            assertThat(response .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getHeaders().get("upload-offset")).isNotEmpty();

            Optional<String> uploadOffset = Optional.ofNullable(response.getHeaders().getFirst("upload-offset"));

            assertThat(uploadOffset).isPresent();
            assertThat(uploadOffset.get()).isEqualTo(Long.toString(chunk.length + offset));
            assertThat(response.getHeaders().getFirst("tus-resumable")).isEqualTo("1.0.0");

            return chunk.length;
        }

        @Test
        @DisplayName("it should create a upload")
        public void createUpload() throws Exception {
            MvcResult result = createUploadRequest();

            List<Video> videos = videoRepository.findAll();

            assertThat(result.getResponse().getHeader("Location"))
                    .isEqualTo(String.format("/files/%s", videos.getFirst().getId()));

            assertThat(videoRepository.count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("it should write upload chunks until finish")
        public void writeUploadChunks() {
            long fileBytesLen = sampleVideoBytesLen();

            String filename = "sample-video.mp4";
            String fileType = "video/mp4";
            String title = "SAMPLE VIDEO";
            List<String> tags = List.of("sample video", "mp4");
            String description = "Sample Video Description";

            CreateVideoUploadInput videoInput = new CreateVideoUploadInput(
                    title,
                    filename,
                    fileType,
                    tags,
                    description,
                    fileBytesLen,
                    String.format("filename %s,filetype %s,title %s,tags %s,description %s",
                            Base64.getEncoder().encodeToString(filename.getBytes()),
                            Base64.getEncoder().encodeToString(fileType.getBytes()),
                            Base64.getEncoder().encodeToString(title.getBytes()),
                            Base64.getEncoder().encodeToString(String.join(",", tags).getBytes()),
                            Base64.getEncoder().encodeToString(description.getBytes())
                    )
            );

            Video video = videoService.createUpload(videoInput);

            String path = "/files/" + video.getId();

            try (InputStream inputStream = getSampleVideoStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long offset = 0L;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byte[] chunk = Arrays.copyOf(buffer, bytesRead);

                    int writtenBytes = writeChunk(video, chunk, offset);

                    offset += writtenBytes;
                }

                assertThat(videoRepository.findAll()).size().isEqualTo(1);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("it should upload a file with pauses")
        public void fileUploadWithPauses() {
            createUploadRequest();

            List<Video> videos = videoRepository.findAll();

            assertThat(videos).size().isEqualTo(1);

            try(InputStream inputStream = getSampleVideoStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long offset = 0L;
                int uploadPauses = 4;
                long bytesPerPause = sampleVideoBytesLen() / uploadPauses;
                int writtenBytes = 0;

                for (int i = 1; i <= uploadPauses; i++) {
                    MvcResult result = mockMvc.perform(
                                    head(String.format("/files/%s", videos.getFirst().getId()))
                                            .header("Tus-Resumable", "1.0.0")

                            )
                            .andDo(print())
                            .andExpect(status().isOk())
                            .andExpect(header().longValue("upload-offset", offset))
                            .andReturn();

                    offset = Long.parseLong(Objects.requireNonNull(result.getResponse().getHeader("upload-offset")));

                    if(i == uploadPauses) {
                        bytesPerPause = sampleVideoBytesLen();
                    }

                    while(writtenBytes <= bytesPerPause && (bytesRead = inputStream.read(buffer)) != -1) {
                        byte[] chunk = Arrays.copyOf(buffer, bytesRead);

                        int wb = writeChunk(videos.getFirst(), chunk, offset);

                        offset += wb;
                        writtenBytes += wb;
                    }

                    writtenBytes = 0;
                }

                mockMvc.perform(
                                head(String.format("/files/%s", videos.getFirst().getId()))
                                        .header("Tus-Resumable", "1.0.0")

                        )
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(header().longValue("upload-offset", sampleVideoBytesLen()))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}