package com.vdt.document_service.dto;

import com.vdt.document_service.entity.DocumentLevel;
import com.vdt.document_service.entity.DocumentType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DocumentRequest(
    @NotBlank String title,
    String description,
    @NotNull DocumentType type,
    @NotNull DocumentLevel level,
    @NotNull @Future LocalDate expiryDate,
    LocalDate effectiveDate   // null = hiệu lực ngay khi được duyệt
) {

}
