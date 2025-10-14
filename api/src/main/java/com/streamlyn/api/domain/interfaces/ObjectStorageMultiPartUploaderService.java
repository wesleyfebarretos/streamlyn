package com.streamlyn.api.domain.interfaces;

import com.streamlyn.api.domain.exception.ApiException;
import com.streamlyn.api.domain.exception.MultiPartUploadException;

import java.io.InputStream;

public interface ObjectStorageMultiPartUploaderService {
    void start(String filePath) throws ApiException;
    long uploadPart(String filePath, InputStream inputStream) throws MultiPartUploadException;
    String complete(String filePath) throws ApiException;
    long minPartSize();
    long minPartSizeOf(long uploadSize);
}
