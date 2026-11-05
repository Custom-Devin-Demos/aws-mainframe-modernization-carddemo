package com.carddemo.online.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CardDemoException.class)
    public ResponseEntity<ScreenResponse<Object>> handle(CardDemoException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ScreenResponse<>(ex.getData(), ex.getMessage(), MessageType.ERROR, ex.getField(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ScreenResponse<Object>> handle(MethodArgumentNotValidException ex) {
        FieldError fe = ex.getBindingResult().getFieldError();
        String message = fe != null ? fe.getDefaultMessage() : "Invalid request";
        return ResponseEntity.badRequest().body(ScreenResponse.error(message, fe != null ? fe.getField() : null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ScreenResponse<Object>> handle(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ScreenResponse.error("Invalid request body", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ScreenResponse<Object>> handle(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ScreenResponse.error("Unexpected error. Please try again ...", null));
    }
}
