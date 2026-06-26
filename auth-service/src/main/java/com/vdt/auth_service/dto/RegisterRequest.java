package com.vdt.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest (
    @NotBlank @Email String email,
    @NotBlank String password,
    @NotBlank String fullName,
    @NotNull String role,
    Long departmentId,
    Long companyId
) {}
