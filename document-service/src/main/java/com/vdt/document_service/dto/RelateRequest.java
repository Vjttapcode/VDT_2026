package com.vdt.document_service.dto;

import com.vdt.document_service.entity.RelationType;

import jakarta.validation.constraints.NotNull;

/** Tạo quan hệ: văn bản hiện tại {type} cho văn bản {targetId}. */
public record RelateRequest(
    @NotNull Long targetId,
    @NotNull RelationType type
) {

}
