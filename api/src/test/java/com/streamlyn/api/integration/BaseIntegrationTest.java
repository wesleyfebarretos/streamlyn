package com.streamlyn.api.integration;

import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
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

    @BeforeAll
    static void setUp() {
        MONGODB.start();
    }

    @DynamicPropertySource
    static void mongoSetup(DynamicPropertyRegistry registry) throws IOException, InterruptedException {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
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
