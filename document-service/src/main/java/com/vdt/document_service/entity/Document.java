package com.vdt.document_service.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    /** Ngày ban hành — hệ thống tự set = ngày được cấp trên phê duyệt. */
    @Column(name = "issued_date")
    private LocalDate issuedDate;

    /** Ngày có hiệu lực — người tạo nhập; null = hiệu lực ngay khi được duyệt. */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "renewal_count", nullable = false)
    private Integer renewalCount;

    /** Văn bản này thay thế văn bản nào (logical ref, null nếu không thay thế gì). */
    @Column(name = "supersedes_id")
    private Long supersedesId;

    /** Phiên bản v{major}.{minor} — bắt đầu 1.0; mỗi lần tái ban hành tăng minor. */
    @Column(name = "version_major", nullable = false)
    private Short versionMajor;

    @Column(name = "version_minor", nullable = false)
    private Short versionMinor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = DocumentStatus.DRAFT;
        if (renewalCount == null) renewalCount = 0;
        if (versionMajor == null) versionMajor = (short) 1;
        if (versionMinor == null) versionMinor = (short) 0;
    }

    /** Chuỗi phiên bản hiển thị, vd "1.0". */
    public String versionString() {
        return versionMajor + "." + versionMinor;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
