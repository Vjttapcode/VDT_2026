package com.vdt.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DepartmentDto(Long id, @NotBlank String name,
        @NotBlank String code, @NotNull Long companyId) {}