package com.vdt.document_service.dto;

import com.vdt.document_service.entity.Document;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DocumentResponse(
        Long id, String title, String description,
        String type, String level, String status,
        Long ownerId, String ownerName, Long departmentId, Long companyId,
        LocalDate expiryDate, String filePath, Integer renewalCount, Long supersedesId,
        LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static DocumentResponse from(Document d) {
        return from(d, null);
    }

    public static DocumentResponse from(Document d, String ownerName) {
        return new DocumentResponse(
                d.getId(), d.getTitle(), d.getDescription(),
                d.getType().name(), d.getLevel().name(), d.getStatus().name(),
                d.getOwnerId(), ownerName, d.getDepartmentId(), d.getCompanyId(),
                d.getExpiryDate(), d.getFilePath(), d.getRenewalCount(), d.getSupersedesId(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}