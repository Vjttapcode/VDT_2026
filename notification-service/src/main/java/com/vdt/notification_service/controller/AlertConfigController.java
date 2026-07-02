package com.vdt.notification_service.controller;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.notification_service.entity.AlertConfig;
import com.vdt.notification_service.repository.AlertConfigRepository;

/**
 * Admin chỉnh ngưỡng cảnh báo theo cấp văn bản (FE Ngày 12).
 * Lưu ý: notification-service chưa có JWT filter — endpoint mở,
 * UI ẩn theo role; ghi rõ limitation trong báo cáo.
 */
@RestController
@RequestMapping("/api/notifications/alert-configs")
public class AlertConfigController {

    private final AlertConfigRepository repo;

    public AlertConfigController(AlertConfigRepository repo) { this.repo = repo; }

    @GetMapping
    public List<AlertConfig> list() {
        return repo.findAll(Sort.by("documentLevel"));
    }

    @PutMapping("/{id}")
    public AlertConfig update(@PathVariable Long id, @RequestBody AlertConfigUpdateRequest req) {
        AlertConfig cfg = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình #" + id));
        // reviewer chốt ngưỡng cảnh báo tối đa 30 ngày; leo thang phải sớm hơn cảnh báo
        if (req.warningDays() < 1 || req.warningDays() > 30)
            throw new IllegalArgumentException("Ngưỡng cảnh báo phải trong 1..30 ngày");
        if (req.escalateDays() < 1 || req.escalateDays() >= req.warningDays())
            throw new IllegalArgumentException("Ngưỡng leo thang phải nhỏ hơn ngưỡng cảnh báo");
        cfg.setWarningDays(req.warningDays());
        cfg.setEscalateDays(req.escalateDays());
        cfg.setEnabled(req.enabled());
        return repo.save(cfg);
    }

    public record AlertConfigUpdateRequest(int warningDays, int escalateDays, boolean enabled) {}
}
