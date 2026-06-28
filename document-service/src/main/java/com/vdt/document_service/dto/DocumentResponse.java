package com.vdt.document_service.dto;

import com.vdt.document_service.entity.Document;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DocumentResponse(
        Long id, String title, String description,
        String type, String level, String status,
        Long ownerId, Long departmentId, Long companyId,
        LocalDate expiryDate, String filePath, Integer renewalCount,
        LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(), d.getTitle(), d.getDescription(),
                d.getType().name(), d.getLevel().name(), d.getStatus().name(),
                d.getOwnerId(), d.getDepartmentId(), d.getCompanyId(),
                d.getExpiryDate(), d.getFilePath(), d.getRenewalCount(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}