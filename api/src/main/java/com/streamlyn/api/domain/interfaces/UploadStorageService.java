package com.streamlyn.api.domain.interfaces;

import com.streamlyn.api.domain.exception.ApiException;

import java.io.InputStream;

public interface UploadStorageService {
    String createUpload(String fileId) throws ApiException;
    InputStream getInputStream(String fileId) throws ApiException;
    long writeChunk(String fileId, long offset, InputStream data, long length) throws ApiException;
    void finalizeUpload(String fileId) throws ApiException;
}

