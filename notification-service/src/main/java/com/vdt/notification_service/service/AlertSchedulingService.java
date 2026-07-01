package com.vdt.notification_service.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vdt.notification_service.client.AuthClient;
import com.vdt.notification_service.client.DocumentClient;
import com.vdt.notification_service.dto.ExpiringDocumentDto;
import com.vdt.notification_service.entity.AlertQueue;
import com.vdt.notification_service.repository.AlertQueueRepository;

@Service
public class AlertSchedulingService {
    private static final Logger log = LoggerFactory.getLogger(AlertSchedulingService.class);
    private final DocumentClient documentClient;
    private final AuthClient authClient;
    private final AlertQueueRepository queueRepo;

    public AlertSchedulingService(DocumentClient documentClient, AuthClient authClient, AlertQueueRepository queueRepo) {
        this.documentClient = documentClient;
        this.authClient = authClient;
        this.queueRepo = queueRepo;
    }

    @Scheduled(cron="0 0 8 * * *")
    public void scheduledRun() {
        runCheck();
    }

    @Transactional
    public int runCheck() {
        List<ExpiringDocumentDto> docs = documentClient.getExpiring(30);
        int n = 0;
        for(ExpiringDocumentDto d : docs){
            String alertType = d.daysLeft() <= 0 ? "EXPIRED" : "WARNING";
            for(Recipient r : resolveRecipients(d)) { 
                queueRepo.save(AlertQueue.builder()
                    .documentId(d.docId()).recipientEmail(r.email()).recipientRole(r.role())
                    .documentLevel(d.level()).departmentId(d.departmentId()).companyId(d.companyId())
                    .daysLeft((int) d.daysLeft()).alertType(alertType).status("PENDING").build());
                n++;
            }
        }
        log.info("[runCheck] {} văn bản -> {} alert vào queue", docs.size(), n);
        return n;
    }

     /** Ma trận leo thang (chỉnh theo alert_configs nếu cần):
     *  - luôn: chủ sở hữu (ownerEmail)
     *  - daysLeft <= 7 : + MANAGER_CENTER (theo departmentId)
     *  - daysLeft <= 0 : + MANAGER_COMPANY (theo companyId) + ADMIN  */
    private List<Recipient> resolveRecipients(ExpiringDocumentDto d) {
        List<Recipient> rs = new ArrayList<>();
        if (d.ownerEmail() != null) rs.add(new Recipient(d.ownerEmail(), "OWNER"));
        if (d.daysLeft() <= 7 && d.departmentId() != null)
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
