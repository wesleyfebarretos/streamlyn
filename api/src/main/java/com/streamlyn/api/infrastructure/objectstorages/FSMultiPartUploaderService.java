package com.streamlyn.api.infrastructure.objectstorages;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.exception.MultiPartUploadException;
import com.streamlyn.api.domain.services.AbstractMultiPartUploaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
@Slf4j
public class FSMultiPartUploaderService extends AbstractMultiPartUploaderService {
    private final Path tmpDir;
    private final Path outputDir;

    public FSMultiPartUploaderService(
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
    public void start(String filePath, String contentType) throws ApiException {
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
    public long uploadPart(String filePath, InputStream is, long contentLength) throws MultiPartUploadException {
        if (!Files.exists(tmpDir.resolve(filePath))) {
            throw ApiException.notFound("failed to upload part, file path was not found");
        }

        long writtenBytes = 0L;

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tmpDir.resolve(filePath).toFile(), true))) {
            byte[] buffer = new byte[50 * 1024 * 1024];
            int bytesRead;

            while((bytesRead = is.readNBytes(buffer, 0, buffer.length)) > 0) {
                bos.write(buffer, 0, bytesRead);
                writtenBytes += bytesRead;
            }
        } catch (IOException e) {
            throw new MultiPartUploadException("could not write all requested bytes", e, writtenBytes);
        }

        return writtenBytes;
    }

    @Override
    public String complete(String filePath) throws ApiException {
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

    @Override
    protected int getMinPartSize() {
        return 5 * 1024 * 1024;
    }

    @Override
    protected int getMaxUploadParts() {
        return Integer.MAX_VALUE;
    }
}