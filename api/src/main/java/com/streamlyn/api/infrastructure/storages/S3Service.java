package com.streamlyn.api.infrastructure.storages;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.interfaces.UploadStorageService;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Slf4j
@RequiredArgsConstructor
public class S3Service implements UploadStorageService {
    private final S3Template s3Template;

    @Value("${object-storage.bucket}")
    private String BUCKET;

    @Override
    public String createUpload(String fileId) throws ApiException {
        return "";
    }

    @Override
    public InputStream getInputStream(String fileId) throws ApiException {
        return null;
    }

    @Override
    public long writeChunk(String fileId, long offset, InputStream data, long length) throws ApiException {
        return 0;
    }

    @Override
    public void finalizeUpload(String fileId) throws ApiException {
    }

    @Scheduled(cron = "* * * * * *")
    public void teste() {
        s3Template.bucketExists(BUCKET);
    }
}
