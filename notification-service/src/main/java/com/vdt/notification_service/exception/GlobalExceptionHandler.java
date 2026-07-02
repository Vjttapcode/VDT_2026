package com.vdt.notification_service.exception;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 502 — service phụ thuộc (document/auth) không phản hồi
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleDownstream(RestClientException e) {
        log.warn("[Noti] downstream không phản hồi: {}", e.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "Service phụ thuộc không phản hồi");
    }

    // 400 — request sai (validate ngưỡng alert-config, id không tồn tại...)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // 404 — gọi endpoint không tồn tại (giữ đúng status, tránh catch-all hạ thành 500)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "Không tìm thấy endpoint");
    }

    // 500 — fallback: không để lộ stacktrace ra client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("[Noti] lỗi không lường trước", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message));
    }
}
