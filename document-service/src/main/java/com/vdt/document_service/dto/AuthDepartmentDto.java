package com.vdt.document_service.dto;

/** Thông tin trung tâm lấy từ auth-service (internal) — chỉ cần id + companyId để suy đích văn bản. */
public record AuthDepartmentDto(Long id, Long companyId) {}
