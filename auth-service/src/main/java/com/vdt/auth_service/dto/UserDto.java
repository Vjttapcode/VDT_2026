package com.vdt.auth_service.dto;

import com.vdt.auth_service.entity.User;

public record UserDto(
        Long id, String email, String fullName, String role,
        Long departmentId, Long companyId, Boolean isActive) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(),
                u.getRole().name(), u.getDepartmentId(), u.getCompanyId(), u.getIsActive());
    }
}