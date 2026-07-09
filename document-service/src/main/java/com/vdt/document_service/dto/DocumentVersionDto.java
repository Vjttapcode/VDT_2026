package com.vdt.document_service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.vdt.document_service.entity.DocumentVersion;

/** Một phiên bản (snapshot) của văn bản trong lịch sử ban hành. */
public record DocumentVersionDto(
        Long id, String version, String title, String description,
        String type, String level, String filePath,
        LocalDate effectiveDate, LocalDate expiryDate, LocalDate issuedDate,
        Long createdBy, String createdByName, LocalDateTime createdAt) {

    public static DocumentVersionDto from(DocumentVersion v, String createdByName) {
        return new DocumentVersionDto(
                v.getId(), v.getVersionMajor() + "." + v.getVersionMinor(),
                v.getTitle(), v.getDescription(),
                v.getType().name(), v.getLevel().name(), v.getFilePath(),
                v.getEffectiveDate(), v.getExpiryDate(), v.getIssuedDate(),
                v.getCreatedBy(), createdByName, v.getCreatedAt());
    }
}
