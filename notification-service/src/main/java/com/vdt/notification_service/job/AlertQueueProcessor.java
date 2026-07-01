package com.vdt.notification_service.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vdt.notification_service.client.DocumentClient;
import com.vdt.notification_service.entity.AlertQueue;
import com.vdt.notification_service.repository.AlertQueueRepository;
import com.vdt.notification_service.service.AlertService;

@Component
public class AlertQueueProcessor {
    private static final Logger log = LoggerFactory.getLogger(AlertQueueProcessor.class);
    private final AlertQueueRepository queueRepo;
    private final AlertService alertService;
    private final DocumentClient documentClient;

    public AlertQueueProcessor(AlertQueueRepository queueRepo, AlertService alertService, DocumentClient documentClient) {
        this.queueRepo = queueRepo;
        this.alertService = alertService;
        this.documentClient = documentClient;
    }

    @Scheduled(fixedDelay=30_000)
    public void process() {
        List<AlertQueue> batch = queueRepo.findTop50ByStatusOrderByCreatedAt("PENDING");
        Map<Long, String> toPatch = new LinkedHashMap<>();
        for(AlertQueue q : batch) {
            alertService.processAlert(q);
            queueRepo.save(q);
            if("PROCESSED".equals(q.getStatus()))
                toPatch.put(q.getDocumentId(), q.getAlertType());
        }
        toPatch.forEach((docId, type) -> documentClient.patchStatus(docId, type));
        if(!batch.isEmpty()) log.info("[AlertQueue] xử lý {} item, PATCH {} văn bản", batch.size(), toPatch.size());
    }
}
