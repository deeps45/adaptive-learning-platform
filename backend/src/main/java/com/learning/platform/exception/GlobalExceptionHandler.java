package com.learning.platform.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<Object> handleForbidden(RuntimeException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex) {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleBadRequest(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", HttpStatus.BAD_REQUEST.value());
        payload.put("error", "Validation failed");
        payload.put("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
    }

    private ResponseEntity<Object> body(HttpStatus status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status.value());
        payload.put("error", message);
        return ResponseEntity.status(status).body(payload);
    }
}
