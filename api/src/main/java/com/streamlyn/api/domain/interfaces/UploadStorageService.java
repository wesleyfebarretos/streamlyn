package com.streamlyn.api.domain.interfaces;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.exception.MultiPartUploadException;

import java.io.InputStream;

public interface UploadStorageService {
    String upload(String filePath, InputStream inputStream);
    void startMultiPartUpload(String filePath) throws ApiException;
    long uploadPart(String filePath, InputStream inputStream) throws MultiPartUploadException;
    String completeMultiPartUpload(String filePath) throws ApiException;
}

