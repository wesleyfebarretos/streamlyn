package com.streamlyn.api.infrastructure.messagebrokers;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.interfaces.MessageBrokerProducer;
import com.streamlyn.entities.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RabbitMQProducerService implements MessageBrokerProducer {
    @Override
    public void publishVideoUploaded(Video video) throws ApiException {
        log.info("Message published to rabbitMQ -> {}", video.getFileUrl());
    }
}
