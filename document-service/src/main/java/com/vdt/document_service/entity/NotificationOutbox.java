package com.vdt.document_service.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name="notification_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationOutbox {
    
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable=false)
    private String eventType;

    @Column(name = "document_id", nullable=false)
    private Long documentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="payload", columnDefinition="jsonb", nullable=false)
    private String payload;

    @Builder.Default
    @Column(name="retry_count", nullable=false)
    private int retryCount = 0;

    @Builder.Default
    @Column(nullable=false)
    private String status = "PENDING";

    @Column(name="created_at", insertable=false, updatable=false)
    private LocalDateTime createdAt;

    @Column(name="sent_at")
    private LocalDateTime sentAt;
}
