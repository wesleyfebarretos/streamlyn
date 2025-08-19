package com.streamlyn.api.infrastructure.storages;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.interfaces.UploadStorageService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

@Component
@Slf4j
public class FSStorageService implements UploadStorageService {
    private final Path tmpDir;
    private final Path outputDir;

    public FSStorageService(
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
    public String createUpload(String fileId) throws ApiException {
        try {
            Path filePath = tmpDir.resolve(fileId);
            Files.createFile(filePath);
            log.info("upload created with id {}", fileId);
            return filePath.toString();
        } catch (IOException e) {
            log.error("failed to create file");
            throw ApiException.internalServerError("Failed to create file: " + e.getMessage());
        }
    }

    @Override
    public InputStream getInputStream(String fileId) throws ApiException {
        return null;
    }

    @Override
    public void writeChunk(String fileId, long offset, InputStream data, long length) throws ApiException {
        try (RandomAccessFile raf = new RandomAccessFile(tmpDir.resolve(fileId).toString(), "rw")) {
            raf.seek(offset);

            byte[] buffer = new byte[8192];
            long remaining = length;

            while (remaining > 0) {
                int bytesToRead = (int) Math.min(buffer.length, remaining);
                int bytesRead = data.read(buffer, 0, bytesToRead);

                if(bytesRead == -1) {
                    break;
                }

                raf.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        } catch (IOException e) {
            log.error("failed to write chunk: ", e);
            throw ApiException.internalServerError("failed to write upload chunk");
        }
    }

    @Override
    public void finalizeUpload(String fileId) throws ApiException {
    }
}