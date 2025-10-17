package com.streamlyn.api.integration;

import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.HashSet;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MongoTemplate mongoTemplate;

    protected static final MongoDBContainer MONGODB = new MongoDBContainer(DockerImageName.parse("mongo:8.0"))
            .withEnv("TZ", ZonedDateTime.now().getZone().getId())
            .withReuse(true)
            .withLogConsumer(frame -> System.out.print(frame.getUtf8String()));

    protected static final MinIOContainer MINIO = new MinIOContainer(DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1"))
            .withUserName("streamlyn")
            .withPassword("streamlyn")
            .withExposedPorts(9000)
            .withReuse(true)
            .withLogConsumer(frame -> System.out.print(frame.getUtf8String()));

    @BeforeAll
    static void startContainers() {
        MONGODB.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void mongoSetup(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }

    @DynamicPropertySource
    static void minIOSetup(DynamicPropertyRegistry registry) {
        String endpoint = String.format("http://%s:%d ",MINIO.getHost(), MINIO.getMappedPort(9000));
        S3Client minio = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("streamlyn", "streamlyn")
                        )
                )
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();

        String BUCKET = "streamlyn";

        minio.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        registry.add("spring.cloud.config.enabled", () -> false);
        registry.add("spring.cloud.config.aws.credentials.access-key", () -> "streamlyn");
        registry.add("spring.cloud.config.aws.credentials.secret-key", () -> "streamlyn");
        registry.add("spring.cloud.config.aws.region", () -> Region.US_EAST_1);
        registry.add("spring.cloud.config.aws.s3.bucket", () -> "streamlyn");
        registry.add("spring.cloud.config.aws.s3.endpoint", () -> endpoint);
        registry.add("spring.cloud.config.aws.s3.path-style-access-enabled", () -> true);
    }

    @AfterEach
    void clearMongoCollections() {
        MongoDatabase db = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase();

        db.listCollectionNames()
                .into(new HashSet<>())
                .stream()
                .filter(name -> !name.startsWith("system."))
                .forEach(collection -> {
                    mongoTemplate.remove(new Query(), collection);
                });
    }
}
