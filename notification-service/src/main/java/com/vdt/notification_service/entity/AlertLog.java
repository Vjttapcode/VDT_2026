package com.vdt.notification_service.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nhật ký gửi cảnh báo. Unique partial index (document_id, recipient_email, alert_type, sent_date)
 * trên các dòng status='SENT' để chống gửi trùng trong cùng một ngày.
 */
@Entity
@Table(name = "alert_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "recipient_role")
    private String recipientRole;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "alert_type", nullable = false)
    private String alertType;          // WARNING | EXPIRED

    @Column(name = "days_left")
    private Integer daysLeft;

    @Column(nullable = false)
    private String status;             // SENT | FAILED

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sent_date", nullable = false)
    private LocalDate sentDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
