package com.vdt.auth_service.service;

import java.util.List;
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
}
