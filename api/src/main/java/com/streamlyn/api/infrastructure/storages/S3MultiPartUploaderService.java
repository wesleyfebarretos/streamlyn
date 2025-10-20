package com.streamlyn.api.infrastructure.storages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.exception.MultiPartUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Primary
@RequiredArgsConstructor
public class S3MultiPartUploaderService extends AbstractMultiPartUploaderService {
    private static final String PART_NUMBER_KEY_PREFIX = "s3:uploads:counters:";
    private static final String PARTS_KEY_PREFIX = "s3:uploads:parts:";
    private static final String UPLOAD_ID_KEY_PREFIX= "s3:uploads:upload_ids:";


    private final S3Client s3Client;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${object-storage.bucket}")
    private String BUCKET;

    @Override
    public void start(String filePath) throws ApiException {
        CreateMultipartUploadResponse createMultipartUploadResponse = s3Client.createMultipartUpload(b -> b
                .bucket(BUCKET)
                .key(filePath));

        this.redisTemplate.opsForValue().set(UPLOAD_ID_KEY_PREFIX.concat(filePath), createMultipartUploadResponse.uploadId());
    }

    @Override
    public int uploadPart(String filePath, InputStream inputStream) throws MultiPartUploadException {
        int writtenBytes = 0;

        try (InputStream is = inputStream) {
            byte[] buffer = new byte[10 * 1024 * 1024];
            int bytesRead;
            int offset = 0;

            String uploadId = redisTemplate.opsForValue().get(UPLOAD_ID_KEY_PREFIX.concat(filePath));

            /**
             * TODO:
             * Make sure that buffer is filled or reach EOF and refactor other methods using CoioteInputStream
             * cause the limit of read is 8192 bytes
             */
            while ((bytesRead = is.readNBytes(buffer, offset, buffer.length - offset)) > 0) {
                Long partNumber = redisTemplate.opsForValue().increment(PART_NUMBER_KEY_PREFIX.concat(filePath));

                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(BUCKET)
                        .key(filePath)
                        .uploadId(uploadId)
                        .partNumber(partNumber.intValue())
                        .build();

                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);

                UploadPartResponse partResponse = s3Client.uploadPart(
                        uploadPartRequest,
                        RequestBody.fromByteBuffer(byteBuffer));

                CompletedPart completedPart = CompletedPart.builder()
                        .partNumber(partNumber.intValue())
                        .eTag(partResponse.eTag())
                        .build();

                Map<String, Object> part = Map.of(
                        "partNumber", completedPart.partNumber(),
                        "eTag", completedPart.eTag()
                );

                ObjectMapper mapper = new ObjectMapper();

                redisTemplate.opsForList().rightPush(PARTS_KEY_PREFIX.concat(filePath), mapper.writeValueAsString(part));

                writtenBytes += bytesRead;
                offset += bytesRead;
            }
        } catch ( JsonProcessingException e) {
            throw ApiException.internalServerError(e.getMessage());
        } catch (IOException e) {
            throw new MultiPartUploadException("could not write all requested bytes", e, writtenBytes);
        }

        return writtenBytes;
    }

    @Override
    public String complete(String filePath) throws ApiException {
        String uploadId = redisTemplate.opsForValue().get(UPLOAD_ID_KEY_PREFIX.concat(filePath));
        if(uploadId == null) {
            throw ApiException.internalServerError("No multi part upload found for " + filePath);
        }

        var partsRaw = redisTemplate.opsForList().range(PARTS_KEY_PREFIX.concat(filePath), 0, -1);

        if(partsRaw == null) {
            throw ApiException.internalServerError("No parts found for file " + filePath);
        }

        ObjectMapper mapper = new ObjectMapper();

        List<CompletedPart> parts = partsRaw.stream()
                .map(completedPart -> {
                    try {
                        Map<String, Object> part = mapper.readValue(completedPart, Map.class);
                        return CompletedPart.builder()
                                .eTag((String) part.get("eTag"))
                                .partNumber((Integer) part.get("partNumber"))
                                .build();
                    } catch(IOException e) {
                        throw ApiException.internalServerError(e.getMessage());
                    }
                })
                .toList();

        CompleteMultipartUploadResponse res = s3Client.completeMultipartUpload(b -> b
                .bucket(BUCKET)
                .key(filePath)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()));

        return res.location();
    }

    @Override
    protected int getMinPartSize() {
        return 5 * 1024 * 1024;
    }

    @Override
    protected int getMaxUploadParts() {
        return 10000;
    }
}
