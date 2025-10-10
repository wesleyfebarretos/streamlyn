package com.streamlyn.api.infrastructure.storages;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.interfaces.UploadStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Primary
public class S3Service implements UploadStorageService {
    private final S3Client s3Client;
    // TODO: Remove this test variable
    private String uploadId;
    // TODO: Remove this test variable
    private Integer partNumber = 0;
    // TODO: Remove this test variable
    private final List<CompletedPart> completedParts = new ArrayList<>();


    @Value("${object-storage.bucket}")
    private String BUCKET;


//    @Scheduled(cron = "* * * * * *")
//    public void teste() {
//        String home = System.getProperty("user.home");
//        File file = new File(home + "/Downloads/test-video.mp4");
//        String filePath = "teste/test-video.mp4";
//
//        startMultiPartUpload(filePath);
//
//        try(InputStream inputStream = new FileInputStream(file)) {
//
//            byte[] buffer = new byte[5 * 1024 * 1024];
//            int bytesRead;
//
//            while((bytesRead = inputStream.read(buffer)) != -1) {
//                uploadPart(filePath, buffer, bytesRead);
//            }
//        } catch (IOException e) {
//            log.error("failed to write chunk: ", e);
//        }
//
//        completeMultiPartUpload(filePath);
//    }

    @Override
    public String upload(String filePath, byte[] buffer) {
        return "";
    }

    @Override
    public void startMultiPartUpload(String filePath) throws ApiException {
        CreateMultipartUploadResponse createMultipartUploadResponse = s3Client.createMultipartUpload(b -> b
                .bucket(BUCKET)
                .key(filePath));

        // TODO: Implement redis to save this upload id
        this.uploadId = createMultipartUploadResponse.uploadId();
    }

    @Override
    public void uploadPart(String filePath, byte[] chunk, int length) throws ApiException {
        // TODO: Remove test variable and implement redis to atomic inc based in filepath key
        partNumber++;

        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(BUCKET)
                .key(filePath)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        UploadPartResponse partResponse = s3Client.uploadPart(
                uploadPartRequest,
                RequestBody.fromBytes(chunk));

        CompletedPart part = CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(partResponse.eTag())
                .build();

        // TODO: Remove this test variable and add this metadata to redis
        completedParts.add(part);
    }

    @Override
    public String completeMultiPartUpload(String filePath) throws ApiException {
        CompleteMultipartUploadResponse res = s3Client.completeMultipartUpload(b -> b
                .bucket(BUCKET)
                .key(filePath)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build()));

        return res.location();
    }
}
