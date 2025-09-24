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
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TusUploadControllerTest extends BaseIntegrationTest {
    private final VideoRepository videoRepository;
    private final VideoService videoService;

    @Nested
    class VideoIngesting {
        private InputStream getSampleVideoStream() throws Exception {
            InputStream inputStream = getClass().getResourceAsStream("/sample-video.mp4");
            if (inputStream == null) {
                throw new IllegalStateException("sample video file not found in resources");
            }

            return inputStream;
        }

        @Test
        @DisplayName("it should create a upload")
        public void createUpload() throws Exception {
            try (InputStream inputStream = getSampleVideoStream()) {
                byte[] fileBytes = inputStream.readAllBytes();

                String filename = Base64.getEncoder().encodeToString("sample-video.mp4".getBytes());
                String fileType = Base64.getEncoder().encodeToString("video/mp4".getBytes());
                String title = Base64.getEncoder().encodeToString("SAMPLE VIDEO".getBytes());

                MvcResult result = mockMvc.perform(
                                post("/files")
                                        .header("Upload-Length", fileBytes.length)
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
                        .isEqualTo(String.format("/files/%s", videos.getFirst().getId()));

                assertThat(videoRepository.count()).isEqualTo(1L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("it should write upload chunks until finish")
        public void writeUploadChunks() {
            File file = new File(Objects.requireNonNull(getClass().getResource("/sample-video.mp4").getFile()));

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
                    file.length(),
                    String.format("filename %s,filetype %s,title %s,tags %s,description %s",
                            Base64.getEncoder().encodeToString(filename.getBytes()),
                            Base64.getEncoder().encodeToString(fileType.getBytes()),
                            Base64.getEncoder().encodeToString(title.getBytes()),
                            Base64.getEncoder().encodeToString(String.join(",", tags).getBytes()),
                            Base64.getEncoder().encodeToString(description.getBytes())
                    )
            );

            Video video = videoService.createUpload(videoInput);

            String url = "/files/" + video.getId();

            try (InputStream inputStream = getSampleVideoStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long offset = 0L;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                    mockMvc.perform(
                                    put(url)
                                            .header("Content-Type", "application/offset+octet-stream")
                                            .header("Content-Length", bytesRead)
                                            .header("Tus-Resumable", "1.0.0")
                                            .header("Upload-Offset", offset)
                                            .content(chunk)
                            )
                            .andDo(print())
//                            .andExpect(status().isCreated())
//                            .andExpect(header().exists("Location"))
//                            .andExpect(header().exists("Tus-Resumable"))
//                            .andExpect(header().string("Tus-Resumable", "1.0.0"))
                            .andReturn();

                    offset += bytesRead;
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}