package com.streamlyn.api.infrastructure.storages;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.interfaces.UploadStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
    public String upload(String filePath, byte[] buffer) {
        return "";
    }

    @Override
    public void startMultiPartUpload(String filePath) throws ApiException {
        try {
            Path file = tmpDir.resolve(filePath);

            if (Files.notExists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }

            Files.createFile(file);
            log.info("upload created with path: {}", filePath);
        } catch (IOException e) {
            throw ApiException.internalServerError("Failed to create file: " + e.getMessage());
        }
    }

    @Override
    public void uploadPart(String filePath, byte[] chunk, int length) throws ApiException {
        if (!Files.exists(tmpDir.resolve(filePath))) {
            throw ApiException.notFound("failed to upload part, file path was not found");
        }

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tmpDir.resolve(filePath).toFile(), true))) {
            bos.write(chunk, 0, length);
        } catch (IOException e) {
            log.error("failed to write chunk: ", e);
        }
    }

    @Override
    public String completeMultiPartUpload(String filePath) throws ApiException {
        Path sourcePath = tmpDir.resolve(filePath);
        Path targetPath = outputDir.resolve(filePath);


        if (Files.notExists(sourcePath)) {
            throw ApiException.notFound("failed to finalize upload, as not found");
        }

        try {
            if(Files.notExists(targetPath.getParent())) {
                Files.createDirectories(targetPath.getParent());
            }

            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("failed to moving file to final directory: ", e);
        }

        return targetPath.toString();
    }
}