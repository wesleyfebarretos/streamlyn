package com.streamlyn.api.domain.interfaces;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.exception.MultiPartUploadException;

import java.io.InputStream;

public interface ObjectStorageMultiPartUploaderService {
    void start(String filePath, String contentType) throws ApiException;
    int uploadPart(String filePath, InputStream inputStream) throws MultiPartUploadException;
    String complete(String filePath) throws ApiException;
    int minPartSize();
    int minPartSizeOf(long uploadSize);
}
