package com.streamlyn.api.domain.interfaces;

import com.streamlyn.api.domain.exception.ApiException;

import java.io.InputStream;

public interface ObjectStorageUploaderService {
    String upload(String filePath, InputStream inputStream) throws ApiException;
}

