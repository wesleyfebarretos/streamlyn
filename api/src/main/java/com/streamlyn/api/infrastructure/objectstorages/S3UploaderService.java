package com.streamlyn.api.infrastructure.objectstorages;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.interfaces.ObjectStorageUploaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.*;
import java.net.URL;

@Component
@Slf4j
@RequiredArgsConstructor
@Primary
public class S3UploaderService implements ObjectStorageUploaderService {
    private final S3Client s3Client;

    @Value("${object-storage.bucket}")
    private String BUCKET;

    @Override
    public String upload(String filePath, InputStream inputStream, String contentType) throws ApiException {
        try(InputStream is = inputStream; ByteArrayOutputStream baos = new ByteArrayOutputStream()){
            is.transferTo(baos);

            byte[] data = baos.toByteArray();

            if(contentType == null) {
                Tika tika = new Tika();
                contentType = tika.detect(data);
            }

            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(filePath)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(req, RequestBody.fromBytes(baos.toByteArray()));


            URL url = s3Client.utilities().getUrl(
                    GetUrlRequest.builder()
                            .bucket(BUCKET)
                            .key(filePath)
                            .build()
            );

            return url.toString();
        } catch (IOException e) {
            throw ApiException.internalServerError("Failed to create file: " + e.getMessage());
        }
    }
}