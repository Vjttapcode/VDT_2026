package com.vdt.document_service.dto;

import java.time.LocalDate;

import com.vdt.document_service.entity.DocumentLevel;
import com.vdt.document_service.entity.DocumentStatus;
import com.vdt.document_service.entity.DocumentType;

/** Admin can thiệp văn bản — chỉ áp dụng các trường khác null. */
public record AdminOverrideRequest(
        String title,
        String description,
        DocumentType type,
        DocumentLevel level,
        LocalDate expiryDate,
        LocalDate effectiveDate,
        Long ownerId,
        DocumentStatus status) {
}
