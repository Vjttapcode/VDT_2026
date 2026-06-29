package com.vdt.document_service.job;

import com.vdt.document_service.client.NotificationClient;
import com.vdt.document_service.entity.NotificationOutbox;
import com.vdt.document_service.repository.NotificationOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelayJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final NotificationOutboxRepository outboxRepo;
    private final NotificationClient notifClient;

    public OutboxRelayJob(NotificationOutboxRepository outboxRepo, NotificationClient notifClient) {
        this.outboxRepo = outboxRepo;
        this.notifClient = notifClient;
    }

    @Scheduled(fixedDelay = 10_000)   // 10s, chờ xong mới chạy tiếp (tránh chồng)
    @Transactional
    public void relay() {
        List<NotificationOutbox> pending =
                outboxRepo.findTop50ByStatusAndRetryCountLessThanOrderByCreatedAt("PENDING", 3);
        for (NotificationOutbox e : pending) {
            try {
                notifClient.sendEmail(e.getEventType(), e.getPayload());
                e.setStatus("SENT");
                e.setSentAt(LocalDateTime.now());
                log.info("[Outbox] SENT id={} type={}", e.getId(), e.getEventType());
            } catch (Exception ex) {
                e.setRetryCount(e.getRetryCount() + 1);
                if (e.getRetryCount() >= 3) e.setStatus("FAILED");
                log.warn("[Outbox] retry={} id={} err={}", e.getRetryCount(), e.getId(), ex.getMessage());
            }
            outboxRepo.save(e);
        }
    }
}
