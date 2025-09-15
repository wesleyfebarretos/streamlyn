package com.streamlyn.api.integration;

import com.mongodb.MongoGridFSException;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.ZonedDateTime;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    protected static final MongoDBContainer MONGODB = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"))
            .withEnv("TZ", ZonedDateTime.now().getZone().getId());

    @DynamicPropertySource
    static void mongoSetup(DynamicPropertyRegistry registry) {
        MONGODB.start();
        System.out.println(MONGODB);
        registry.add("spring.data.mongodb.host", MONGODB::getHost);
        registry.add("spring.data.mongodb.port", MONGODB::getFirstMappedPort);
    }

}
