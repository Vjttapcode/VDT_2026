package com.vdt.document_service.dto;

import jakarta.validation.constraints.NotNull;

/** Đánh dấu văn bản hiện tại thay thế cho văn bản {supersededId}. */
public record ReplaceRequest(
    @NotNull Long supersededId
) {

}
