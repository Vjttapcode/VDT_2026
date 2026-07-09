package com.vdt.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vdt.notification_service.entity.AlertLog;
import com.vdt.notification_service.entity.AlertQueue;
import com.vdt.notification_service.repository.AlertLogRepository;
import com.vdt.notification_service.repository.AlertQueueRepository;

class AlertServiceTest {

    private EmailService emailService;
    private AlertLogRepository logRepo;
    private AlertService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        logRepo = mock(AlertLogRepository.class);
        service = new AlertService(emailService, logRepo, mock(AlertQueueRepository.class), 3);
        // chưa gửi SENT hôm nay -> không bị dedup chặn
        when(logRepo.existsByDocumentIdAndRecipientEmailAndAlertTypeAndSentDateAndStatus(
                anyLong(), anyString(), anyString(), any(LocalDate.class), anyString()))
            .thenReturn(false);
    }

    private AlertQueue queueItem(int retryCount) {
        return AlertQueue.builder().documentId(1L).recipientEmail("a@b.vn")
            .recipientRole("STAFF").documentLevel("COMPANY").daysLeft(7)
            .alertType("WARNING").retryCount(retryCount).build();
    }

    @Test
    void guiFail_lanDau_giuPending_tangRetry_khongGhiLog() {
        doThrow(new RuntimeException("smtp timeout")).when(emailService)
            .sendExpiryAlert(anyString(), anyLong(), anyString(), anyLong(), anyString());
        AlertQueue q = queueItem(0);

        service.processAlert(q);

        assertThat(q.getStatus()).isEqualTo("PENDING");
        assertThat(q.getRetryCount()).isEqualTo(1);
        verify(logRepo, never()).save(any());
    }

    @Test
    void guiFail_qua3LanThu_chuyenFailed_ghiLogFailed() {
        doThrow(new RuntimeException("smtp down")).when(emailService)
            .sendExpiryAlert(anyString(), anyLong(), anyString(), anyLong(), anyString());
        AlertQueue q = queueItem(2); // lần thử thứ 3 (retryCount 2 + 1 = 3 = maxRetries)

        service.processAlert(q);

        assertThat(q.getStatus()).isEqualTo("FAILED");
        ArgumentCaptor<AlertLog> cap = ArgumentCaptor.forClass(AlertLog.class);
        verify(logRepo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(cap.getValue().getErrorMessage()).isEqualTo("smtp down");
    }

    @Test
    void guiThanhCong_processed_ghiLogSent() {
        AlertQueue q = queueItem(0);

        service.processAlert(q);

        assertThat(q.getStatus()).isEqualTo("PROCESSED");
        ArgumentCaptor<AlertLog> cap = ArgumentCaptor.forClass(AlertLog.class);
        verify(logRepo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("SENT");
    }
}
