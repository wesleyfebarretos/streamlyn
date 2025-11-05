package com.streamlyn.api.domain.interfaces;

import com.streamlyn.api.domain.events.VideoUploadedEvent;
import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.entities.Video;

public interface MessageBrokerProducer {
    void publishVideoUploaded(VideoUploadedEvent event) throws ApiException;
}
