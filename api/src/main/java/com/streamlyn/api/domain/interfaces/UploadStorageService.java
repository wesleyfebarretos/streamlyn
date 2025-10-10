package com.streamlyn.api.domain.interfaces;

import com.streamlyn.api.domain.exception.ApiException;

import java.io.InputStream;

public interface UploadStorageService {
    String upload(String filePath, byte[] data);
    void startMultiPartUpload(String filePath) throws ApiException;
    void uploadPart(String filePath, byte[] chunk, int length) throws ApiException;
    String completeMultiPartUpload(String filePath) throws ApiException;
}

