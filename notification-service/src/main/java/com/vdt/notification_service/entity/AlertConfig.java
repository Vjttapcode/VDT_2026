package com.vdt.notification_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ngưỡng cảnh báo theo cấp văn bản (CENTER/COMPANY/GROUP). Admin chỉnh ở FE Ngày 12.
 * - warning_days : daysLeft <= warning_days  -> bắt đầu cảnh báo WARNING cho chủ sở hữu
 * - escalate_days: daysLeft <= escalate_days -> leo thang lên quản lý cấp trên
 */
@Entity
@Table(name = "alert_configs",
        uniqueConstraints = @UniqueConstraint(name = "uq_alert_config_level", columnNames = "document_level"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_level", nullable = false)
    private String documentLevel;      // CENTER | COMPANY | GROUP

    @Builder.Default
    @Column(name = "warning_days", nullable = false)
    private int warningDays = 30;

    @Builder.Default
    @Column(name = "escalate_days", nullable = false)
    private int escalateDays = 7;

    /** Các mốc ngày trước hết hạn sẽ gửi nhắc, vd "30,15,7,1". Rỗng = nhắc mỗi ngày trong warningDays. */
    @Builder.Default
    @Column(name = "remind_days", nullable = false)
    private String remindDays = "30,15,7,1";

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;
}
