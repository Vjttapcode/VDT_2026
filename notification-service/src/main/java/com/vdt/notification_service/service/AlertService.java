package com.vdt.notification_service.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.vdt.notification_service.entity.AlertLog;
import com.vdt.notification_service.entity.AlertQueue;
import com.vdt.notification_service.repository.AlertLogRepository;

import jakarta.transaction.Transactional;

@Service
public class AlertService {
    private final EmailService emailService;
    private final AlertLogRepository alertLogRepository;

    public AlertService(EmailService emailService, AlertLogRepository alertLogRepository) {
        this.emailService = emailService;
        this.alertLogRepository = alertLogRepository;
    }

    @Transactional
    public void processAlert(AlertQueue q) {
        LocalDate today = LocalDate.now();
        boolean already = alertLogRepository.existsByDocumentIdAndRecipientEmailAndAlertTypeAndSentDateAndStatus(
            q.getDocumentId(), q.getRecipientEmail(), q.getAlertType(), today, "SENT");
        if(already){
            q.setStatus("PROCESSED");
            q.setProcessedAt(LocalDateTime.now());
            return;
        }
        try {
            emailService.sendExpiryAlert(q.getRecipientEmail(), q.getDocumentId(), q.getDocumentLevel(), q.getDaysLeft(), q.getAlertType());
            saveLog(q, "SENT", null);
            q.setStatus("PROCESSED");
        } catch (Exception e) {
            saveLog(q, "FAILED", e.getMessage());
            q.setStatus("FAILED");
        }
        q.setProcessedAt(LocalDateTime.now());
    }

    private void saveLog(AlertQueue q, String status, String err) {
        alertLogRepository.save(AlertLog.builder().documentId(q.getDocumentId()).recipientEmail(q.getRecipientEmail())
            .recipientRole(q.getRecipientRole()).departmentId(q.getDepartmentId()).daysLeft(q.getDaysLeft()).alertType(q.getAlertType()).status(status)
            .errorMessage(err).sentDate(LocalDate.now()).build());
    }
}
