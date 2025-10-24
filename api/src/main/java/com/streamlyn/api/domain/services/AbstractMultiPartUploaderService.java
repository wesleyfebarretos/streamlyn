package com.streamlyn.api.domain.services;

import com.streamlyn.api.domain.interfaces.ObjectStorageMultiPartUploaderService;

public abstract class AbstractMultiPartUploaderService implements ObjectStorageMultiPartUploaderService {
    protected abstract int getMinPartSize();
    protected abstract int getMaxUploadParts();

    @Override
    public int minPartSize() {
        return getMinPartSize();
    }

    @Override
    public int minPartSizeOf(long uploadSize) {
        long partSize = uploadSize / getMaxUploadParts();

        if(partSize > Integer.MAX_VALUE) {
            partSize = Integer.MAX_VALUE;
        }

        return Math.max(getMinPartSize(),  (int) partSize);
    }
}
