package com.vdt.notification_service.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vdt.notification_service.client.AuthClient;
import com.vdt.notification_service.client.DocumentClient;
import com.vdt.notification_service.dto.ExpiringDocumentDto;
import com.vdt.notification_service.entity.AlertConfig;
import com.vdt.notification_service.entity.AlertQueue;
import com.vdt.notification_service.repository.AlertConfigRepository;
import com.vdt.notification_service.repository.AlertQueueRepository;

@Service
public class AlertSchedulingService {
    private static final Logger log = LoggerFactory.getLogger(AlertSchedulingService.class);
    private final DocumentClient documentClient;
    private final AuthClient authClient;
    private final AlertQueueRepository queueRepo;
    private final AlertConfigRepository configRepo;

    public AlertSchedulingService(DocumentClient documentClient, AuthClient authClient,
            AlertQueueRepository queueRepo, AlertConfigRepository configRepo) {
        this.documentClient = documentClient;
        this.authClient = authClient;
        this.queueRepo = queueRepo;
        this.configRepo = configRepo;
    }

    @Scheduled(cron="0 0 8 * * *")
    public void scheduledRun() {
        runCheck();
    }

    @Transactional
    public int runCheck() {
        // preload cấu hình theo cấp văn bản (CENTER/COMPANY/GROUP)
        Map<String, AlertConfig> configs = new HashMap<>();
        for (AlertConfig c : configRepo.findAll()) configs.put(c.getDocumentLevel(), c);

        // lấy trong cửa sổ tối đa (30) rồi lọc theo warningDays từng cấp
        List<ExpiringDocumentDto> docs = documentClient.getExpiring(30);
        int n = 0, skipped = 0;
        for (ExpiringDocumentDto d : docs) {
            AlertConfig cfg = configs.get(d.level());
            if (!shouldAlert(d, cfg)) { skipped++; continue; }

            String alertType = d.daysLeft() <= 0 ? "EXPIRED" : "WARNING";
            for (Recipient r : resolveRecipients(d, cfg)) {
                queueRepo.save(AlertQueue.builder()
                    .documentId(d.docId()).recipientEmail(r.email()).recipientRole(r.role())
                    .documentLevel(d.level()).departmentId(d.departmentId()).companyId(d.companyId())
                    .daysLeft((int) d.daysLeft()).alertType(alertType).status("PENDING").build());
                n++;
            }
        }
        log.info("[runCheck] {} văn bản (bỏ qua {}) -> {} alert vào queue", docs.size(), skipped, n);
        return n;
    }

    /** Có nên cảnh báo văn bản này theo cấu hình cấp của nó không. */
    private boolean shouldAlert(ExpiringDocumentDto d, AlertConfig cfg) {
        if (cfg == null || !cfg.isEnabled()) return false;          // tắt cấp này
        long daysLeft = d.daysLeft();
        if (daysLeft > cfg.getWarningDays()) return false;          // chưa tới ngưỡng cảnh báo
        if (daysLeft <= 0) return true;                             // đã/đang quá hạn: luôn nhắc
        Set<Integer> milestones = parseMilestones(cfg.getRemindDays());
        // rỗng = nhắc mỗi ngày trong warningDays; có mốc = chỉ nhắc đúng mốc
        return milestones.isEmpty() || milestones.contains((int) daysLeft);
    }

    private Set<Integer> parseMilestones(String remindDays) {
        Set<Integer> ms = new HashSet<>();
        if (remindDays == null || remindDays.isBlank()) return ms;
        for (String p : remindDays.split(",")) {
            try { ms.add(Integer.parseInt(p.trim())); } catch (NumberFormatException ignored) {}
        }
        return ms;
    }

    /** Ma trận leo thang lấy từ alert_configs:
     *  - luôn: chủ sở hữu (ownerEmail)
     *  - daysLeft <= escalateDays : + MANAGER_CENTER (theo departmentId)
     *  - daysLeft <= 0            : + MANAGER_COMPANY (theo companyId) + ADMIN  */
    private List<Recipient> resolveRecipients(ExpiringDocumentDto d, AlertConfig cfg) {
        List<Recipient> rs = new ArrayList<>();
        if (d.ownerEmail() != null) rs.add(new Recipient(d.ownerEmail(), "OWNER"));
        if (d.daysLeft() <= cfg.getEscalateDays() && d.departmentId() != null)
            addIfPresent(rs, authClient.centerManagerEmail(d.departmentId()), "MANAGER_CENTER");
        if (d.daysLeft() <= 0) {
            if (d.companyId() != null)
                addIfPresent(rs, authClient.companyManagerEmail(d.companyId()), "MANAGER_COMPANY");
            addIfPresent(rs, authClient.adminEmail(), "ADMIN");
        }
        return rs;
    }
    private void addIfPresent(List<Recipient> rs, String email, String role) {
        if (email != null && rs.stream().noneMatch(x -> x.email().equals(email)))
            rs.add(new Recipient(email, role));
    }

    private record Recipient(String email, String role) {}
}
