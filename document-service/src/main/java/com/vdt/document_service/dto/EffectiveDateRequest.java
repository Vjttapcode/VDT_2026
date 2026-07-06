package com.vdt.document_service.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Đổi ngày hiệu lực thủ công cho văn bản APPROVED; đặt <= hôm nay = kích hoạt ngay. */
public record EffectiveDateRequest(@NotNull LocalDate effectiveDate) {
}
