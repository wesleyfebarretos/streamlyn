package com.streamlyn.api.infrastructure.storages;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.interfaces.UploadStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

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
    public long writeChunk(String fileId, long offset, InputStream data, long length) {
        long writtenBytes = 0L;

        if(!Files.exists(Path.of(fileId))) {
            throw ApiException.notFound("failed to write chunk, target file was not found");
        }

        try (RandomAccessFile raf = new RandomAccessFile(fileId, "rw")) {
            raf.seek(offset);

            byte[] buffer = new byte[8192];
            long remaining = length;

            while (remaining > 0) {
                int bytesToRead = (int) Math.min(buffer.length, remaining);
                int readedBytes = data.read(buffer, 0, bytesToRead);

                if(readedBytes == -1) {
                    break;
                }

                raf.write(buffer, 0, readedBytes);
                writtenBytes += readedBytes;
                remaining -= readedBytes;
            }
        } catch (IOException e) {
            log.error("failed to write chunk: ", e);
        }

        return writtenBytes;
    }

    @Override
    public void finalizeUpload(String fileId) throws ApiException {
        Path sourcePath = tmpDir.resolve(fileId);
        Path targetPath = outputDir.resolve(new File(fileId).getName());

        if(!Files.exists(sourcePath)) {
            throw ApiException.notFound("failed to finalize upload, source file was not found");
        }

        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("failed to moving file to final directory: ", e);
        }
    }
}