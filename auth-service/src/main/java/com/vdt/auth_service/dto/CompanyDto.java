package com.vdt.auth_service.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanyDto(Long id, @NotBlank String name, @NotBlank String code) {}
