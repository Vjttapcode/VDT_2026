package com.vdt.notification_service.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.notification_service.entity.AlertLog;
import com.vdt.notification_service.repository.AlertLogRepository;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final AlertLogRepository logRepo;

    public NotificationController(AlertLogRepository logRepo) {
        this.logRepo = logRepo;
    }

    /** Nhật ký cảnh báo, lọc theo phòng (departmentId) và khoảng ngày (from/to).
     *  Mặc định 30 ngày gần nhất nếu không truyền from/to. */
    @GetMapping("/alert-logs")
    public List<AlertLog> alertLogs(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now();
        return departmentId != null
                ? logRepo.findByDepartmentIdAndSentDateBetweenOrderByCreatedAtDesc(departmentId, f, t)
                : logRepo.findBySentDateBetweenOrderByCreatedAtDesc(f, t);
    }
}
