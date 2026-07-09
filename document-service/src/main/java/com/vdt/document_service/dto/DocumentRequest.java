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
    LocalDate effectiveDate,   // null = hiệu lực ngay khi được duyệt
    // Đích áp dụng — chỉ dùng khi phạm vi người tạo rộng hơn cấp đã chọn:
    //   CENTER  + (ADMIN / MANAGER_COMPANY) -> departmentId (bắt buộc)
    //   COMPANY + ADMIN                      -> companyId (bắt buộc)
    // Các trường hợp còn lại backend tự suy từ phạm vi người tạo (bỏ qua 2 trường này).
    Long departmentId,
    Long companyId
) {

}
