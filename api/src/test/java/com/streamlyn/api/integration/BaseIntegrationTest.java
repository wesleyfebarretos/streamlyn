package com.streamlyn.api.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.ZonedDateTime;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {
    @Autowired
    protected MockMvc mockMvc;

    protected static final MongoDBContainer MONGODB = new MongoDBContainer(DockerImageName.parse("mongo:8.0"))
            .withEnv("TZ", ZonedDateTime.now().getZone().getId());

    @DynamicPropertySource
    static void mongoSetup(DynamicPropertyRegistry registry) {
        MONGODB.start();
        System.out.println("REPLICA -> " + MONGODB.getReplicaSetUrl());
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }
}
