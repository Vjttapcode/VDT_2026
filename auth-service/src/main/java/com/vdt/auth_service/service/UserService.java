package com.vdt.auth_service.service;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.vdt.auth_service.dto.UserDto;
import com.vdt.auth_service.entity.Role;
import com.vdt.auth_service.entity.User;
import com.vdt.auth_service.exception.BusinessException;
import com.vdt.auth_service.exception.NotFoundException;
import com.vdt.auth_service.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public List<UserDto> findAll() {
        return userRepo.findAll().stream().map(UserDto::from).toList();
    }

    public UserDto update(Long id, UserDto dto) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user id=" + id));
        user.setFullName(dto.fullName());
        if (dto.isActive() != null) user.setIsActive(dto.isActive());
        // đổi role kèm org scope — validate giống register, khớp chk_users_org_scope trong DB
        if (dto.role() != null) {
            Role role;
            try {
                role = Role.valueOf(dto.role());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Role không hợp lệ: " + dto.role());
            }
            boolean ok = switch (role) {
                case ADMIN -> dto.departmentId() == null && dto.companyId() == null;
                case MANAGER_COMPANY -> dto.departmentId() == null && dto.companyId() != null;
                case MANAGER_CENTER, USER -> dto.departmentId() != null && dto.companyId() == null;
            };
            if (!ok) throw new BusinessException("departmentId/companyId không khớp với role " + role);
            user.setRole(role);
            user.setDepartmentId(dto.departmentId());
            user.setCompanyId(dto.companyId());
        }
        return UserDto.from(userRepo.save(user));
    }

    /**
     * Xóa vĩnh viễn một tài khoản (chỉ ADMIN — chặn ở SecurityConfig).
     * Không cho tự xóa tài khoản của mình để tránh admin tự khóa quyền quản trị.
     * Văn bản do user này sở hữu vẫn giữ trong document-service (owner_id là logical ref,
     * không FK chéo schema) và hiển thị tên dự phòng "Người dùng #id".
     */
    public void delete(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user id=" + id));
        Long currentUserId = currentUserId();
        if (user.getId().equals(currentUserId))
            throw new BusinessException("Không thể tự xóa tài khoản của chính mình");
        userRepo.delete(user);
    }

    /** Id user hiện tại từ JWT (principal do JwtAuthenticationFilter đặt là userId). */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    }
}
