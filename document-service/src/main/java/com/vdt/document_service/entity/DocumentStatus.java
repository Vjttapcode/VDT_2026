package com.vdt.document_service.entity;

public enum DocumentStatus {
    DRAFT,
    PENDING,
    APPROVED,   // đã duyệt, chờ đến ngày hiệu lực
    ACTIVE,
    WARNING,
    EXPIRED,
    REJECTED
}
