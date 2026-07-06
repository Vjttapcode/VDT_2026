package com.vdt.document_service.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vdt.document_service.service.DocumentService;

/**
 * Kích hoạt hiệu lực tự động: APPROVED có effective_date <= hôm nay -> ACTIVE.
 * Chỉ là lớp phòng hộ nền — list()/dashboardStats()/findExpiring() và mọi thao tác mở văn bản
 * (qua findOrThrow) đã tự kích hoạt ngay khi cần, nên job này không cần chạy sát giờ; chạy mỗi 5 phút
 * để phòng trường hợp không ai chủ động đọc/mở văn bản đó (an toàn nhờ activateIfDueAtomic chống trùng).
 */
@Component
public class EffectiveDateJob {

    private static final Logger log = LoggerFactory.getLogger(EffectiveDateJob.class);

    private final DocumentService documentService;

    public EffectiveDateJob(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void run() {
        int n = documentService.activateDueDocuments();
        if (n > 0) log.info("[EffectiveDate] {} văn bản chuyển APPROVED -> ACTIVE", n);
    }
}
