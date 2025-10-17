package com.streamlyn.api.integration;

import com.mongodb.client.MongoDatabase;
import com.redis.testcontainers.RedisContainer;
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
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

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

    protected static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName
                    .parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1")
                    .asCompatibleSubstituteFor("minio/minio")
    )
            .withUserName("streamlyn")
            .withPassword("streamlyn")
            .withExposedPorts(9000)
            .withReuse(true)
            .withLogConsumer(frame -> System.out.print(frame.getUtf8String()));

    protected static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7.4.6-alpine"))
            .withExposedPorts(6379)
            .withReuse(true)
            .withCommand("redis-server --appendonly yes")
            .withLogConsumer(frame -> System.out.print(frame.getUtf8String()));

    @BeforeAll
    static void startContainers() {
        CompletableFuture.allOf(
                CompletableFuture.runAsync(MONGODB::start),
                CompletableFuture.runAsync(MINIO::start),
                CompletableFuture.runAsync(REDIS::start)
        ).join();
    }

    @DynamicPropertySource
    static void mongoSetup(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }

    @DynamicPropertySource
    static void minIOSetup(DynamicPropertyRegistry registry) {
        S3Client minio = S3Client.builder()
                .endpointOverride(URI.create(MINIO.getS3URL()))
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
        registry.add("spring.cloud.aws.credentials.access-key", MINIO::getUserName);
        registry.add("spring.cloud.aws.credentials.secret-key", MINIO::getPassword);
        registry.add("spring.cloud.aws.region", () -> Region.US_EAST_1);
        registry.add("spring.cloud.aws.s3.bucket", () -> BUCKET);
        registry.add("spring.cloud.aws.s3.endpoint", MINIO::getS3URL);
        registry.add("spring.cloud.aws.s3.path-style-access-enabled", () -> true);
    }

    @DynamicPropertySource
    static void redisSetup(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
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
