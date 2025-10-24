package com.streamlyn.api.integration;

import com.streamlyn.api.domain.inputs.CreateVideoUploadInput;
import com.streamlyn.api.domain.interfaces.ObjectStorageMultiPartUploaderService;
import com.streamlyn.api.domain.repositories.VideoRepository;
import com.streamlyn.api.domain.services.VideoService;
import com.streamlyn.entities.Video;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MvcResult;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TusUploadControllerTest extends BaseIntegrationTest {
    private final VideoRepository videoRepository;
    private final VideoService videoService;
    private final TestRestTemplate restTemplate;
    private final ObjectStorageMultiPartUploaderService multiPartUploaderService;
    @Value("${app.url}")
    private String appUrl;

    @Value("${tus.max-size}")
    private String tusMaxSize;


    @Nested
    class VideoIngesting {
        @Test
        @DisplayName("it should create a upload")
        public void createUpload() throws Exception {
            long uploadSize = sampleVideoUploadSize("/sample-video-1mb.mp4");

            String filename = Base64.getEncoder().encodeToString("sample-video.mp4".getBytes());
            String fileType = Base64.getEncoder().encodeToString("video/mp4".getBytes());
            String title = Base64.getEncoder().encodeToString("SAMPLE VIDEO".getBytes());

            try {
                MvcResult result = mockMvc.perform(
                                post("/tus/videos")
                                        .header("Upload-Length", Long.toString(uploadSize))
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

                List<Video> videos = videoRepository.findAll();

                assertThat(result.getResponse().getHeader("Location"))
                        .isEqualTo(String.format("%s/tus/videos/%s", appUrl, videos.getFirst().getId()));

                assertThat(videoRepository.count()).isEqualTo(1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("it should upload the entire file in one chunk")
        public void upload() throws Exception {
            long uploadSize = sampleVideoUploadSize("/sample-video-1mb.mp4");

            String filename = "sample-video-1mb.mp4";
            String fileType = "video/mp4";
            String title = "SAMPLE VIDEO 1MB";
            List<String> tags = List.of("sample video 1mb", "mp4");
            String description = "Sample Video 1MB Description";


            Video video = videoService.save(buildVideoInput(
                    filename,
                    fileType,
                    title,
                    tags,
                    description,
                    uploadSize
            ));

            try (InputStream inputStream = getSampleVideoStream("/sample-video-1mb.mp4")) {
                byte[] buffer = new byte[(int) uploadSize];

                inputStream.read(buffer);

                int writtenBytes = uploadChunk(video, buffer, 0L);


                assertThat(writtenBytes).isEqualTo(uploadSize);

                List<Video> videos = videoRepository.findAll();

                assertThat(videos).size().isEqualTo(1);
                assertThat(videos.getFirst().getOffset()).isEqualTo(uploadSize);
                assertThat(videos.getFirst().getFileUrl()).contains(videos.getFirst().getId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("it should upload the entire file in many chunks")
        public void uploadInChunks() throws Exception {
            Video video = create10MBVideo();

            videoService.startMultiPartUpload(video);

            final int MIN_CHUNK_SIZE = multiPartUploaderService.minPartSizeOf(video.getUploadLength());

            long writtenBytes = 0;
            long offset = 0L;

            try (InputStream inputStream = getSampleVideoStream("/sample-video-10mb.mp4")) {
                byte[] buffer = new byte[MIN_CHUNK_SIZE];
                int bytesRead;

                while((bytesRead = inputStream.read(buffer)) != -1) {
                    byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                    int wb = uploadChunk(video, chunk, offset);
                    offset += wb;
                    writtenBytes += wb;
                }


                List<Video> videos = videoRepository.findAll();

                assertThat(writtenBytes).isEqualTo(video.getUploadLength());
                assertThat(videos).size().isEqualTo(1);
                assertThat(videos.getFirst().getFileUrl()).contains(videos.getFirst().getId());
                assertThat(videos.getFirst().getOffset()).isEqualTo(video.getUploadLength());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("it should upload a file with pauses")
        public void fileUploadWithPauses() {
            Video video = create10MBVideo();
            videoService.startMultiPartUpload(video);

            List<Video> videos = videoRepository.findAll();

            assertThat(videos).size().isEqualTo(1);

            final int MIN_CHUNK_SIZE = multiPartUploaderService.minPartSizeOf(video.getUploadLength());

            try(InputStream inputStream = getSampleVideoStream("/sample-video-10mb.mp4")) {
                byte[] buffer = new byte[MIN_CHUNK_SIZE];
                int bytesRead;
                long offset = 0L;
                int uploadPauses = 4;
                long uploadSize = sampleVideoUploadSize("/sample-video-10mb.mp4");
                long bytesPerPause =  uploadSize / uploadPauses;
                int writtenBytes = 0;

                for (int i = 1; i <= uploadPauses; i++) {
                    MvcResult result = mockMvc.perform(
                                    head(String.format("/tus/videos/%s", videos.getFirst().getId()))
                                            .header("Tus-Resumable", "1.0.0")

                            )
                            .andDo(print())
                            .andExpect(status().isOk())
                            .andExpect(header().longValue("upload-offset", offset))
                            .andReturn();

                    offset = Long.parseLong(Objects.requireNonNull(result.getResponse().getHeader("upload-offset")));

                    if(i == uploadPauses) {
                        bytesPerPause = uploadSize;
                    }

                    while(writtenBytes <= bytesPerPause && (bytesRead = inputStream.read(buffer)) != -1) {
                        byte[] chunk = Arrays.copyOf(buffer, bytesRead);

                        int wb = uploadChunk(videos.getFirst(), chunk, offset);

                        offset += wb;
                        writtenBytes += wb;
                    }

                    writtenBytes = 0;
                }

                mockMvc.perform(
                                head(String.format("/tus/videos/%s", videos.getFirst().getId()))
                                        .header("Tus-Resumable", "1.0.0")

                        )
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(header().longValue("upload-offset", uploadSize));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }


        private InputStream getSampleVideoStream(String filename) throws Exception {
            InputStream inputStream = getClass().getResourceAsStream(filename);
            if (inputStream == null) {
                throw new IllegalStateException("sample video file not found in resources");
            }

            return new BufferedInputStream(inputStream);
        }

        private long sampleVideoUploadSize(String filename) {
            return new File(Objects.requireNonNull(getClass().getResource(filename)).getFile()).length();
        }

        private int uploadChunk(Video video, byte[] chunk, long offset) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/offset+octet-stream");
            headers.add("Content-Length", String.valueOf(chunk.length));
            headers.add("Tus-Resumable", "1.0.0");
            headers.add("Upload-Offset", String.valueOf(offset));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(chunk, headers);

            String path = "/tus/videos/" + video.getId();

            ResponseEntity<Void> response = restTemplate.exchange(path, HttpMethod.PATCH, requestEntity, Void.class);

            assertThat(response .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getHeaders().get("upload-offset")).isNotEmpty();

            Optional<String> uploadOffset = Optional.ofNullable(response.getHeaders().getFirst("upload-offset"));

            assertThat(uploadOffset).isPresent();
            assertThat(uploadOffset.get()).isEqualTo(Long.toString(chunk.length + offset));
            assertThat(response.getHeaders().getFirst("tus-resumable")).isEqualTo("1.0.0");

            return chunk.length;
        }

        private CreateVideoUploadInput buildVideoInput(
                String filename,
                String fileType,
                String title,
                List<String> tags,
                String description,
                long uploadSize
        ) {
            return new CreateVideoUploadInput(
                    title,
                    filename,
                    fileType,
                    tags,
                    description,
                    uploadSize,
                    String.format("filename %s,filetype %s,title %s,tags %s,description %s",
                            Base64.getEncoder().encodeToString(filename.getBytes()),
                            Base64.getEncoder().encodeToString(fileType.getBytes()),
                            Base64.getEncoder().encodeToString(title.getBytes()),
                            Base64.getEncoder().encodeToString(String.join(",", tags).getBytes()),
                            Base64.getEncoder().encodeToString(description.getBytes())
                    )
            );
        }

        private Video create10MBVideo() {
            long uploadSize = sampleVideoUploadSize("/sample-video-10mb.mp4");

            String filename = "sample-video-10mb.mp4";
            String fileType = "video/mp4";
            String title = "SAMPLE VIDEO 10MB";
            List<String> tags = List.of("sample video 10mb", "mp4");
            String description = "Sample Video 10MB Description";

            return videoService.save(buildVideoInput(
                    filename,
                    fileType,
                    title,
                    tags,
                    description,
                    uploadSize
            ));
        }
    }

    @Nested
    class ServerConfiguration {
        @Test
        @DisplayName("it should get the current server configuration")
        public void getServerConfiguration() throws Exception {
            mockMvc.perform(
                            options("/tus/videos")
                    )
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("it should ensure protocol version contains 1.0.0")
        public void protocolVersion() throws Exception{
            mockMvc.perform(
                            options("/tus/videos")
                    )
                    .andDo(print())
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Tus-Resumable", containsString("1.0.0")));
        }

        @Test
        @DisplayName("it should ensure Tus-Max-Size is config based")
        public void tusMaxSize() throws Exception{
            mockMvc.perform(
                            options("/tus/videos")
                    )
                    .andDo(print())
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Tus-Max-Size", tusMaxSize));
        }

        @Test
        @DisplayName("it should ensure that Tus-Extension is available at headers with the right value")
        public void tusExtensions() throws Exception{
            mockMvc.perform(
                            options("/tus/videos")
                    )
                    .andDo(print())
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Tus-Extension", "creation,expiration"));
        }

        @Test
        @DisplayName("it should ensure that Tus-Min-Chunk-Size is available at headers with the right value")
        public void tusMinChunkSize() throws Exception{
            mockMvc.perform(
                            options("/tus/videos")
                    )
                    .andDo(print())
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Tus-Min-Chunk-Size", String.valueOf(multiPartUploaderService.minPartSize())));
        }
    }
}