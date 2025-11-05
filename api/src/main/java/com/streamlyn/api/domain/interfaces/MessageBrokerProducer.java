package com.streamlyn.api.domain.interfaces;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.entities.Video;

public interface MessageBrokerProducer {
    void publishVideoUploaded(Video video) throws ApiException;
}
