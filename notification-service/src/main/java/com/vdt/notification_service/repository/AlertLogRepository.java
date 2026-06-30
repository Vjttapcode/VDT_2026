package com.vdt.notification_service.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vdt.notification_service.entity.AlertLog;

public interface AlertLogRepository extends JpaRepository<AlertLog, Long>{
    boolean existsByDocumentIdAndRecipientEmailAndAlertTypeAndSentDateAndStatus(
        Long documentId, String recipientEmail, String alertType, LocalDate sentDate, String status);
    
    List<AlertLog> findByDepartmentIdAndSentDateBetweenOrderByCreatedAtDesc(Long deptId, LocalDate from, LocalDate to);
}
