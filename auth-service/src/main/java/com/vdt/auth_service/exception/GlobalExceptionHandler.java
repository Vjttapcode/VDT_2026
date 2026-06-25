package com.vdt.auth_service.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — không tìm thấy tài nguyên
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<?> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // 400 — vi phạm nghiệp vụ (email trùng, role sai, org scope sai...)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> business(BusinessException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // 400 — body fail @Valid (lấy lỗi field đầu tiên cho gọn)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Dữ liệu không hợp lệ");
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    // 500 — fallback: không để lộ stacktrace ra client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> generic(Exception e) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");
    }

    private ResponseEntity<?> body(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", msg));
    }
}