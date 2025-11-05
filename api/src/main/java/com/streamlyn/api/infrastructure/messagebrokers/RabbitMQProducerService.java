package com.streamlyn.api.infrastructure.messagebrokers;

import com.streamlyn.api.domain.events.VideoUploadedEvent;
import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.interfaces.MessageBrokerProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitMQProducerService implements MessageBrokerProducer {
    private final RabbitTemplate rabbitTemplate;
    private static final String VIDEO_PUBLISHED_FANOUT_EXCHANGE = "video.published.fanout";

    @Override
    public void publishVideoUploaded(VideoUploadedEvent event) throws ApiException {
        log.info("Publishing to exchange {} -> {}", VIDEO_PUBLISHED_FANOUT_EXCHANGE, event);
        rabbitTemplate.convertAndSend(VIDEO_PUBLISHED_FANOUT_EXCHANGE, "", event);
    }
}
