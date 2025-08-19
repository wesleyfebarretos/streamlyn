package com.streamlyn.api.domain.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException{
    private HttpStatus statusCode;

    public ApiException(HttpStatus statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException internalServerError(String message) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public static ApiException payloadTooLarge (String message){
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    public static ApiException unsupportedMediaType(String message) {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public HttpStatus getStatusCode() {
        return statusCode;
    }
}
