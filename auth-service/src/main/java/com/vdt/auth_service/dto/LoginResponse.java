package com.vdt.auth_service.dto;

public record LoginResponse (
    String token,
    Long userId,
    String email,
    String fullName,
    String role,
    Long departmentId,
    Long companyId
) {}
