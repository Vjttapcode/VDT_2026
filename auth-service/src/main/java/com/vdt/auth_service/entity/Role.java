package com.vdt.auth_service.entity;

/**
 * 4 vai trò trong hệ thống — tương ứng tổ chức 3 tầng:
 * Tập đoàn (ADMIN) > Công ty (MANAGER_COMPANY) > Trung tâm (MANAGER_CENTER) > Nhân viên (USER).
 */
public enum Role {
    ADMIN,
    MANAGER_COMPANY,
    MANAGER_CENTER,
    USER
}
