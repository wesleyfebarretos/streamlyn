package com.streamlyn.api.web.filters;

import com.streamlyn.api.domain.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class ExceptionHandlerFilter {
    @ExceptionHandler(value = ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException error) {
        log.error("application log ->", error);

        return new ResponseEntity<>(Map.of(
                "code", String.valueOf(error.getStatusCode().value()),
                "message", error.getMessage()
        ), error.getStatusCode());
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleRequestException(MethodArgumentNotValidException error) {
        log.error("application log ->", error);
        return new ResponseEntity<>(Map.of(
                "code", String.valueOf(error.getStatusCode().value()),
                "message", Arrays.stream(error.getDetailMessageArguments())
                        .map(Object::toString)
                        .collect(Collectors.joining(""))
        ), error.getStatusCode());
    }

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception error) {
        log.error("application log ->", error);
        return new ResponseEntity<>(Map.of(
                "code", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                "message", error.getMessage()
        ), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
