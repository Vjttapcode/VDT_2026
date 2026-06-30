package com.vdt.notification_service.entity;

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

@Entity
@Table(name="alert_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertQueue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "document_id", nullable = false)     private Long documentId;
    @Column(name = "recipient_email", nullable = false)  private String recipientEmail;
    @Column(name = "recipient_role", nullable = false)   private String recipientRole;
    @Column(name = "document_level", nullable = false)   private String documentLevel;
    @Column(name = "department_id")                      private Long departmentId;
    @Column(name = "company_id")                         private Long companyId;
    @Column(name = "days_left", nullable = false)        private int daysLeft;
    @Column(name = "alert_type", nullable = false)       private String alertType;
    @Builder.Default @Column(nullable = false)           private String status = "PENDING";
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "processed_at")                       private LocalDateTime processedAt;
}
