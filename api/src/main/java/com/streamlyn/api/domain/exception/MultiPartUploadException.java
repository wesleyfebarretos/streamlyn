package com.streamlyn.api.domain.exception;

public class MultiPartUploadException extends Exception {
    private long writtenBytes = 0;

    public MultiPartUploadException(String message, long writtenBytes) {
        super(message);
        this.writtenBytes = writtenBytes;
    }

    public MultiPartUploadException(String message, Throwable cause, long writtenBytes) {
        super(message, cause);
        this.writtenBytes = writtenBytes;
    }

    public long getWrittenBytes() {
        return writtenBytes;
    }
}
