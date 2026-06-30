package com.vdt.notification_service.dto;

public record ExpiringDocumentDto(
    Long docId,
    String level,
    long daysLeft,
    Long departmentId,
    Long companyId,
    String ownerEmail
) {}
