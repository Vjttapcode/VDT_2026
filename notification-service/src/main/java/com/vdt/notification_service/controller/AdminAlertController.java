package com.vdt.notification_service.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.notification_service.client.AuthClient;
import com.vdt.notification_service.entity.AlertLog;
import com.vdt.notification_service.repository.AlertLogRepository;
import com.vdt.notification_service.service.AlertSchedulingService;
import com.vdt.notification_service.service.AlertService;

/** Hành động quản trị cảnh báo (ADMIN — gate ở SecurityConfig). */
@RestController
@RequestMapping("/api/notifications/admin")
public class AdminAlertController {

    private final AlertSchedulingService scheduling;
    private final AlertService alertService;
    private final AuthClient authClient;
    private final AlertLogRepository logRepo;

    public AdminAlertController(AlertSchedulingService scheduling, AlertService alertService,
            AuthClient authClient, AlertLogRepository logRepo) {
        this.scheduling = scheduling;
        this.alertService = alertService;
        this.authClient = authClient;
        this.logRepo = logRepo;
    }

    /** Thống kê gửi cảnh báo 30 ngày gần nhất. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        List<AlertLog> logs = logRepo.findBySentDateBetweenOrderByCreatedAtDesc(from, to);
        long sent = logs.stream().filter(l -> "SENT".equals(l.getStatus())).count();
        long failed = logs.stream().filter(l -> "FAILED".equals(l.getStatus())).count();
        long warning = logs.stream().filter(l -> "WARNING".equals(l.getAlertType())).count();
        long expired = logs.stream().filter(l -> "EXPIRED".equals(l.getAlertType())).count();
        return Map.of("total", logs.size(), "sent", sent, "failed", failed,
                "warning", warning, "expired", expired);
    }

    /** Chạy quét cảnh báo ngay (thay vì chờ cron 8h sáng). */
    @PostMapping("/run-check")
    public Map<String, Object> runCheck() {
        int enqueued = scheduling.runCheck();
        return Map.of("status", "ok", "enqueued", enqueued);
    }

    /** Gửi lại một cảnh báo đã FAILED. */
    @PostMapping("/resend/{logId}")
    public Map<String, Object> resend(@PathVariable Long logId) {
        alertService.resend(logId);
        return Map.of("status", "ok");
    }

    /** Gửi email cảnh báo thử (mặc định về hòm thư admin). */
    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody(required = false) TestRequest req) {
        String email = (req != null && req.email() != null && !req.email().isBlank())
                ? req.email() : authClient.adminEmail();
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Không xác định được email nhận thử");
        alertService.sendTest(email);
        return Map.of("status", "ok", "email", email);
    }

    public record TestRequest(String email) {}
}
