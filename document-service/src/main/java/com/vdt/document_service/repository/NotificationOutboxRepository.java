package com.vdt.document_service.repository;

import com.vdt.document_service.entity.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findTop50ByStatusAndRetryCountLessThanOrderByCreatedAt(String status, int maxRetry);
    
}
