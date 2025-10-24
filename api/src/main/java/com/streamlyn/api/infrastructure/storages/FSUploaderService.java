package com.streamlyn.api.infrastructure.storages;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.interfaces.ObjectStorageUploaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Slf4j
public class FSUploaderService implements ObjectStorageUploaderService {
    private final Path tmpDir;
    private final Path outputDir;

    public FSUploaderService(
        @Value("${object-storage.tmp-dir}") String tmpDir,
        @Value("${object-storage.output-dir}") String outputDir
    ) throws ApiException {
        String cwd = System.getProperty("user.dir");
        this.tmpDir = Path.of(cwd, tmpDir);
        this.outputDir = Path.of(cwd, outputDir);

        try {
            if (!Files.exists(this.tmpDir)) {
                Files.createDirectories(this.tmpDir);
            }

            if (!Files.exists(this.outputDir)) {
                Files.createDirectories(this.outputDir);
            }

        } catch (IOException e) {
            throw ApiException.internalServerError("Failed to create storage directories: " + e.getMessage());
        }
    }

    @Override
    public String upload(String filePath, InputStream inputStream, String contentType) throws ApiException {
        try {
            Path filepath = outputDir.resolve(filePath);

            if (Files.notExists(filepath.getParent())) {
                Files.createDirectories(filepath.getParent());
            }

            Files.createFile(filepath);

            try(BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filepath.toFile(), true))) {
                byte[] buffer = new byte[20 * 1024 * 1024];
                int bytesRead;

                while((bytesRead = inputStream.readNBytes(buffer, 0, buffer.length)) > 0) {
                    bos.write(buffer, 0, bytesRead);
                }
            }

            log.info("upload created with path: {}", filepath);
            return filepath.toString();
        } catch (IOException e) {
            throw ApiException.internalServerError("Failed to create file: " + e.getMessage());
        }
    }
}