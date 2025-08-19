package com.streamlyn.api.domain.repositories;

import com.streamlyn.entities.Video;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VideoRepository extends MongoRepository<Video, String> {}
