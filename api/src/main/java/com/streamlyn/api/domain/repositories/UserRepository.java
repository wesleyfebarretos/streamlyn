package com.streamlyn.api.domain.repositories;

import com.streamlyn.entities.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {}
