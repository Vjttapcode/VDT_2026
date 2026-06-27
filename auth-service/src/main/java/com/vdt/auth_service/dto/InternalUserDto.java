package com.vdt.auth_service.dto;

import com.vdt.auth_service.entity.User;

public record InternalUserDto(Long id, String email, String fullName, String role) {
    public static InternalUserDto from(User u) {
        return new InternalUserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name());
    }
}
